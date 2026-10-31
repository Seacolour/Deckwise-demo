package com.xiaoyuan.paperpuppybackendintl.api.chatgpt;

import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputFile;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseInputText;
import com.openai.models.responses.ResponseIncludable;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.Tool;
import com.openai.models.responses.WebSearchTool;
import com.openai.core.JsonValue;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * “应用式”调用：instructions + input -> Responses API。
 */
@Slf4j
public class OpenAiApplicationExecutor implements AiExecutor<ApplicationParam, String>, StreamingAiExecutor<ApplicationParam> {

    @Override
    public String call(ApplicationParam param) {
        return callWithResult(param).outputText();
    }

    public OpenAiApplicationResult callWithResult(ApplicationParam param) {
        Objects.requireNonNull(param, "param");
        long totalStartNs = System.nanoTime();
        List<String> fallbackModels = AiOptionReader.stringListOpt(param, "model.fallbackModels");

        // primary + fallback models
        List<String> models = fallbackModels.isEmpty()
                ? List.of(param.model())
                : concatModels(param.model(), fallbackModels);

        RuntimeException last = null;
        for (int i = 0; i < models.size(); i++) {
            String model = models.get(i);
            ApplicationParam p2 = param.toBuilder().model(model).build();
            long attemptStartNs = System.nanoTime();
            try {
                OpenAiApplicationResult result = doCallOnce(p2);
                log.debug("openai responses attempt timing: model={}, attempt={}, totalAttempts={}, success=true, elapsedMs={}, totalCallMs={}",
                        model,
                        i + 1,
                        models.size(),
                        elapsedMillis(attemptStartNs),
                        elapsedMillis(totalStartNs));
                return result;
            } catch (RuntimeException e) {
                last = e;
                log.warn("openai responses attempt timing: model={}, attempt={}, totalAttempts={}, success=false, elapsedMs={}, totalCallMs={}, errorType={}, errorMessage={}",
                        model,
                        i + 1,
                        models.size(),
                        elapsedMillis(attemptStartNs),
                        elapsedMillis(totalStartNs),
                        e.getClass().getSimpleName(),
                        summarizeThrowable(e));
            }
        }
        throw last == null ? new RuntimeException("OpenAI call failed") : last;
    }

    @Override
    public String stream(ApplicationParam param, Consumer<String> deltaConsumer) {
        return streamWithResult(param, deltaConsumer).outputText();
    }

    public OpenAiApplicationResult streamWithResult(ApplicationParam param, Consumer<String> deltaConsumer) {
        Objects.requireNonNull(param, "param");
        Consumer<String> safeDeltaConsumer = deltaConsumer == null ? ignored -> {
        } : deltaConsumer;
        long totalStartNs = System.nanoTime();
        List<String> fallbackModels = AiOptionReader.stringListOpt(param, "model.fallbackModels");
        List<String> models = fallbackModels.isEmpty()
                ? List.of(param.model())
                : concatModels(param.model(), fallbackModels);

        RuntimeException last = null;
        for (int i = 0; i < models.size(); i++) {
            String model = models.get(i);
            ApplicationParam p2 = param.toBuilder().model(model).build();
            long attemptStartNs = System.nanoTime();
            AtomicBoolean sawDelta = new AtomicBoolean(false);
            try {
                OpenAiApplicationResult result = doStreamOnce(p2, delta -> {
                    sawDelta.set(true);
                    safeDeltaConsumer.accept(delta);
                });
                log.debug("openai responses stream attempt timing: model={}, attempt={}, totalAttempts={}, success=true, elapsedMs={}, totalCallMs={}",
                        model,
                        i + 1,
                        models.size(),
                        elapsedMillis(attemptStartNs),
                        elapsedMillis(totalStartNs));
                return result;
            } catch (RuntimeException e) {
                last = e;
                log.warn("openai responses stream attempt timing: model={}, attempt={}, totalAttempts={}, success=false, sawDelta={}, elapsedMs={}, totalCallMs={}, errorType={}, errorMessage={}",
                        model,
                        i + 1,
                        models.size(),
                        sawDelta.get(),
                        elapsedMillis(attemptStartNs),
                        elapsedMillis(totalStartNs),
                        e.getClass().getSimpleName(),
                        summarizeThrowable(e));
                if (sawDelta.get()) {
                    throw e;
                }
            }
        }
        throw last == null ? new RuntimeException("OpenAI stream call failed") : last;
    }

    private static OpenAiApplicationResult doCallOnce(ApplicationParam param) {
        long totalStartNs = System.nanoTime();
        long phaseStartNs = totalStartNs;
        OpenAIClient client = OpenAiClients.get(param);
        long clientLookupMs = elapsedMillis(phaseStartNs);
        phaseStartNs = System.nanoTime();
        String model = Objects.toString(param.model(), "").trim();
        if (model.isBlank()) throw new IllegalArgumentException("model is blank");

        String input = Objects.toString(param.input(), "");
        String instructions = Objects.toString(param.instructions(), "").trim();
        String textVerbosity = AiOptionReader.stringOpt(param, "model.textVerbosity", null);
        String reasoningEffort = AiOptionReader.stringOpt(param, "model.reasoningEffort", null);
        String serviceTier = OpenAiServiceTierSupport.resolve(param);
        ResponseCreateParams requestParams = buildCreateParams(param, model, input, instructions, serviceTier);
        long buildRequestMs = elapsedMillis(phaseStartNs);

        phaseStartNs = System.nanoTime();
        var resp = client.responses().create(requestParams);
//        log.info("openai responses raw response: {}", resp);
//        log.info("openai responses raw output: {}", resp.output());
        long apiCreateMs = elapsedMillis(phaseStartNs);
        phaseStartNs = System.nanoTime();
        String refusal = OpenAiTextExtractor.extractRefusal(resp.output());
        long refusalExtractMs = elapsedMillis(phaseStartNs);
        if (refusal != null && !refusal.isBlank()) {
            // 对于 structured outputs 场景，refusal 不应被当成文本输出；这里直接抛出，交给上层处理
            throw new IllegalStateException("OpenAI refused: " + refusal);
        }
        phaseStartNs = System.nanoTime();
        String outputText = OpenAiTextExtractor.extractOutputText(resp.output());
        OpenAiWebSearchMetadata webSearch = OpenAiTextExtractor.extractWebSearchMetadata(resp.output());
        PromptCacheUsage promptCacheUsage = promptCacheUsage(resp);
        long outputExtractMs = elapsedMillis(phaseStartNs);
        log.debug("openai responses create timing: model={}, serviceTier={}, reasoningEffort={}, textVerbosity={}, inputChars={}, cacheableInputChars={}, fileUrls={}, imageUrls={}, instructionsChars={}, schemaEntries={}, inputTokens={}, cachedTokens={}, cacheWriteTokens={}, outputTokens={}, outputItems={}, outputChars={}, webSearchCalls={}, webSearchSources={}, clientLookupMs={}, buildRequestMs={}, apiCreateMs={}, refusalExtractMs={}, outputExtractMs={}, totalMs={}",
                model,
                blankToNull(serviceTier),
                blankToNull(reasoningEffort),
                blankToNull(textVerbosity),
                input.length(),
                Objects.toString(param.cacheableInput(), "").length(),
                normalizeUrls(param.fileUrls()).size(),
                normalizeInputImages(param).size(),
                instructions.length(),
                param.jsonSchema() == null ? 0 : param.jsonSchema().size(),
                promptCacheUsage.inputTokens(),
                promptCacheUsage.cachedTokens(),
                promptCacheUsage.cacheWriteTokens(),
                promptCacheUsage.outputTokens(),
                resp.output() == null ? 0 : resp.output().size(),
                outputText.length(),
                webSearch.calls().size(),
                webSearch.sources().size(),
                clientLookupMs,
                buildRequestMs,
                apiCreateMs,
                refusalExtractMs,
                outputExtractMs,
                elapsedMillis(totalStartNs));
        logPromptCacheUsage("blocking", param, model, promptCacheUsage);
        logWebSearchMetadata("blocking", param, model, webSearch);
        return new OpenAiApplicationResult(outputText, webSearch);
    }

    private static OpenAiApplicationResult doStreamOnce(ApplicationParam param, Consumer<String> deltaConsumer) {
        long totalStartNs = System.nanoTime();
        long phaseStartNs = totalStartNs;
        OpenAIClient client = OpenAiClients.get(param);
        long clientLookupMs = elapsedMillis(phaseStartNs);
        phaseStartNs = System.nanoTime();
        String model = Objects.toString(param.model(), "").trim();
        if (model.isBlank()) throw new IllegalArgumentException("model is blank");

        String input = Objects.toString(param.input(), "");
        String instructions = Objects.toString(param.instructions(), "").trim();
        String textVerbosity = AiOptionReader.stringOpt(param, "model.textVerbosity", null);
        String reasoningEffort = AiOptionReader.stringOpt(param, "model.reasoningEffort", null);
        String serviceTier = OpenAiServiceTierSupport.resolve(param);
        ResponseCreateParams requestParams = buildCreateParams(param, model, input, instructions, serviceTier);
        long buildRequestMs = elapsedMillis(phaseStartNs);

        phaseStartNs = System.nanoTime();
        StringBuilder output = new StringBuilder();
        List<ResponseOutputItem> outputItems = new ArrayList<>();
        long[] firstDeltaMs = {-1L};
        long[] eventCount = {0L};
        String[] doneText = {null};
        PromptCacheUsage[] promptCacheUsage = {PromptCacheUsage.empty()};
        try (StreamResponse<ResponseStreamEvent> stream = client.responses().createStreaming(requestParams)) {
            stream.stream().forEach(event -> {
                eventCount[0]++;
                event.error().ifPresent(error -> {
                    throw new IllegalStateException("OpenAI stream error: " + error.message());
                });
                event.refusalDelta().ifPresent(refusal -> {
                    throw new IllegalStateException("OpenAI refused: " + refusal.delta());
                });
                event.failed().ifPresent(failed -> {
                    throw new IllegalStateException("OpenAI response failed: "
                            + failed.response().status().map(Object::toString).orElse("failed"));
                });
                event.incomplete().ifPresent(incomplete -> {
                    throw new IllegalStateException("OpenAI response incomplete: "
                            + incomplete.response().incompleteDetails().map(Object::toString).orElse("incomplete"));
                });
                event.outputTextDelta().ifPresent(textDelta -> {
                    String delta = textDelta.delta();
                    if (delta == null || delta.isEmpty()) {
                        return;
                    }
                    if (firstDeltaMs[0] < 0) {
                        firstDeltaMs[0] = elapsedMillis(totalStartNs);
                    }
                    output.append(delta);
                    deltaConsumer.accept(delta);
                });
                event.outputTextDone().ifPresent(textDone -> doneText[0] = textDone.text());
                event.completed().ifPresent(completed -> promptCacheUsage[0] = promptCacheUsage(completed.response()));
                event.outputItemDone().ifPresent(done -> {
                    try {
                        outputItems.add(done.item());
                    } catch (RuntimeException ignored) {
                    }
                });
            });
        }
        if (output.isEmpty() && doneText[0] != null && !doneText[0].isBlank()) {
            output.append(doneText[0]);
        }
        long apiStreamMs = elapsedMillis(phaseStartNs);
        OpenAiWebSearchMetadata webSearch = OpenAiTextExtractor.extractWebSearchMetadata(outputItems);
        log.debug("openai responses stream timing: model={}, serviceTier={}, reasoningEffort={}, textVerbosity={}, inputChars={}, cacheableInputChars={}, fileUrls={}, imageUrls={}, instructionsChars={}, schemaEntries={}, inputTokens={}, cachedTokens={}, cacheWriteTokens={}, outputTokens={}, streamEvents={}, firstDeltaMs={}, outputChars={}, webSearchCalls={}, webSearchSources={}, clientLookupMs={}, buildRequestMs={}, apiStreamMs={}, totalMs={}",
                model,
                blankToNull(serviceTier),
                blankToNull(reasoningEffort),
                blankToNull(textVerbosity),
                input.length(),
                Objects.toString(param.cacheableInput(), "").length(),
                normalizeUrls(param.fileUrls()).size(),
                normalizeInputImages(param).size(),
                instructions.length(),
                param.jsonSchema() == null ? 0 : param.jsonSchema().size(),
                promptCacheUsage[0].inputTokens(),
                promptCacheUsage[0].cachedTokens(),
                promptCacheUsage[0].cacheWriteTokens(),
                promptCacheUsage[0].outputTokens(),
                eventCount[0],
                firstDeltaMs[0],
                output.length(),
                webSearch.calls().size(),
                webSearch.sources().size(),
                clientLookupMs,
                buildRequestMs,
                apiStreamMs,
                elapsedMillis(totalStartNs));
        logPromptCacheUsage("streaming", param, model, promptCacheUsage[0]);
        logWebSearchMetadata("streaming", param, model, webSearch);
        return new OpenAiApplicationResult(output.toString(), webSearch);
    }

    private static void logWebSearchMetadata(String mode,
                                             ApplicationParam param,
                                             String model,
                                             OpenAiWebSearchMetadata webSearch) {
        if (!log.isDebugEnabled() || webSearch == null) {
            return;
        }
        if (webSearch.isEmpty()) {
            if (param != null && param.webSearchEnabled()) {
                log.debug("openai web search details: mode={}, model={}, schema={}, enabled=true, used=false, contextSize={}",
                        mode,
                        model,
                        param.jsonSchemaName(),
                        param.webSearchContextSize());
            }
            return;
        }
        log.debug("openai web search details: mode={}, model={}, schema={}, enabled={}, contextSize={}, calls={}, sources={}, citations={}, queries={}, sourceUrls={}, citationUrls={}",
                mode,
                model,
                param == null ? null : param.jsonSchemaName(),
                param != null && param.webSearchEnabled(),
                param == null ? null : param.webSearchContextSize(),
                summarizeSearchCalls(webSearch),
                webSearch.sources().size(),
                webSearch.citations().size(),
                summarizeSearchQueries(webSearch, 8),
                summarizeSearchSources(webSearch, 8),
                summarizeSearchCitations(webSearch, 8));
    }

    private static List<String> summarizeSearchCalls(OpenAiWebSearchMetadata webSearch) {
        List<String> calls = new ArrayList<>();
        for (OpenAiWebSearchMetadata.WebSearchCall call : webSearch.calls()) {
            String query = firstNonBlank(call.query(), String.join(" | ", call.queries()));
            calls.add("id=" + truncate(call.id(), 80)
                    + ", action=" + truncate(call.action(), 40)
                    + ", status=" + truncate(call.status(), 40)
                    + ", query=" + truncate(query, 180)
                    + ", sourceUrls=" + summarizeStrings(call.sourceUrls(), 3, 140));
            if (calls.size() >= 6) {
                break;
            }
        }
        return calls;
    }

    private static List<String> summarizeSearchQueries(OpenAiWebSearchMetadata webSearch, int maxItems) {
        Set<String> seen = new LinkedHashSet<>();
        for (OpenAiWebSearchMetadata.WebSearchCall call : webSearch.calls()) {
            addSummaryText(seen, call.query(), 180, maxItems);
            for (String query : call.queries()) {
                addSummaryText(seen, query, 180, maxItems);
            }
            if (seen.size() >= maxItems) {
                break;
            }
        }
        return new ArrayList<>(seen);
    }

    private static List<String> summarizeSearchSources(OpenAiWebSearchMetadata webSearch, int maxItems) {
        Set<String> seen = new LinkedHashSet<>();
        for (OpenAiWebSearchMetadata.WebSearchSource source : webSearch.sources()) {
            addSummaryText(seen, formatTitleUrl(source.title(), source.url()), 220, maxItems);
            if (seen.size() >= maxItems) {
                break;
            }
        }
        return new ArrayList<>(seen);
    }

    private static List<String> summarizeSearchCitations(OpenAiWebSearchMetadata webSearch, int maxItems) {
        Set<String> seen = new LinkedHashSet<>();
        for (OpenAiWebSearchMetadata.WebSearchCitation citation : webSearch.citations()) {
            addSummaryText(seen, formatTitleUrl(citation.title(), citation.url()), 220, maxItems);
            if (seen.size() >= maxItems) {
                break;
            }
        }
        return new ArrayList<>(seen);
    }

    private static List<String> summarizeStrings(List<String> values, int maxItems, int maxChars) {
        Set<String> seen = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                addSummaryText(seen, value, maxChars, maxItems);
                if (seen.size() >= maxItems) {
                    break;
                }
            }
        }
        return new ArrayList<>(seen);
    }

    private static void addSummaryText(Set<String> target, String value, int maxChars, int maxItems) {
        if (target.size() >= maxItems) {
            return;
        }
        String text = truncate(value, maxChars);
        if (!text.isBlank()) {
            target.add(text);
        }
    }

    private static String formatTitleUrl(String title, String url) {
        String safeTitle = truncate(title, 100);
        String safeUrl = truncate(url, 180);
        if (safeTitle.isBlank()) {
            return safeUrl;
        }
        if (safeUrl.isBlank()) {
            return safeTitle;
        }
        return safeTitle + " <" + safeUrl + ">";
    }

    private static String firstNonBlank(String first, String fallback) {
        String value = Objects.toString(first, "").trim();
        return value.isBlank() ? Objects.toString(fallback, "").trim() : value;
    }

    private static String truncate(String value, int maxChars) {
        if (value == null || maxChars <= 0) {
            return "";
        }
        String text = value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() <= maxChars ? text : text.substring(0, maxChars).trim();
    }

    static ResponseCreateParams buildCreateParams(ApplicationParam param,
                                                  String model,
                                                  String input,
                                                  String instructions,
                                                  String serviceTier) {
        ResponseCreateParams.Builder b = ResponseCreateParams.builder()
                .model(model)
                .inputOfResponse(buildInputItems(param, input));

        String promptCacheKey = Objects.toString(param.promptCacheKey(), "").trim();
        if (!promptCacheKey.isBlank()) {
            b.promptCacheKey(promptCacheKey);
        }
        if (param.explicitPromptCaching() && supportsExplicitPromptCaching(model)) {
            b.putAdditionalBodyProperty("prompt_cache_options", JsonValue.from(Map.of(
                    "mode", "explicit",
                    "ttl", "30m"
            )));
        }

        if (!instructions.isBlank()) {
            // 注意：如果未来需要更细粒度（developer/system），可改用 input items 里增加对应 role
            b.instructions(instructions);
        }

        ResponseTextConfig.Builder textBuilder = null;

        // Structured Outputs（Responses API 的 text.format json_schema）
        if (param.jsonSchema() != null && !param.jsonSchema().isEmpty()) {
            ResponseFormatTextJsonSchemaConfig.Schema schema = schemaFromMap(param.jsonSchema());
            ResponseFormatTextJsonSchemaConfig fmt = ResponseFormatTextJsonSchemaConfig.builder()
                    .name(Objects.toString(param.jsonSchemaName(), "output"))
                    .schema(schema)
                    .strict(param.jsonSchemaStrict())
                    .build();
            if (param.jsonSchemaDescription() != null && !param.jsonSchemaDescription().isBlank()) {
                fmt = fmt.toBuilder().description(param.jsonSchemaDescription()).build();
            }
            textBuilder = ResponseTextConfig.builder().format(fmt);
        }

        String textVerbosity = AiOptionReader.stringOpt(param, "model.textVerbosity", null);
        if (textVerbosity != null && !textVerbosity.isBlank()) {
            textBuilder = textBuilder == null ? ResponseTextConfig.builder() : textBuilder;
            textBuilder.verbosity(ResponseTextConfig.Verbosity.of(textVerbosity.trim().toLowerCase()));
        }
        if (textBuilder != null) {
            b.text(textBuilder.build());
        }

        List<Tool> tools = buildTools(param);
        if (!tools.isEmpty()) {
            b.tools(tools);
            if (param.webSearchEnabled()) {
                b.addInclude(ResponseIncludable.WEB_SEARCH_CALL_ACTION_SOURCES);
            }
        }

        // 透传常见可选项（不存在就忽略）
        double temperature = AiOptionReader.doubleOpt(param, "model.temperature", Double.NaN);
        if (!Double.isNaN(temperature)) {
            trySetDouble(b, "temperature", temperature);
        }
        double topP = AiOptionReader.doubleOpt(param, "model.topP", Double.NaN);
        if (!Double.isNaN(topP)) {
            trySetDouble(b, "topP", topP);
        }
        long maxOutputTokens = AiOptionReader.intOpt(param, "model.maxOutputTokens", -1);
        if (maxOutputTokens > 0) {
            trySetLong(b, "maxOutputTokens", maxOutputTokens);
        }
        String reasoningEffort = AiOptionReader.stringOpt(param, "model.reasoningEffort", null);
        if (reasoningEffort != null && !reasoningEffort.isBlank() && supportsReasoningEffort(model)) {
            b.reasoning(Reasoning.builder()
                    .effort(ReasoningEffort.of(reasoningEffort.trim().toLowerCase()))
                    .build());
        }
        if (serviceTier != null && !serviceTier.isBlank()) {
            b.serviceTier(ResponseCreateParams.ServiceTier.of(serviceTier));
        }
        return b.build();
    }

    static boolean supportsReasoningEffort(String model) {
        String normalized = Objects.toString(model, "").trim().toLowerCase();
        if (normalized.isBlank()) {
            return false;
        }
        return normalized.startsWith("gpt-5")
                || normalized.startsWith("o1")
                || normalized.startsWith("o3")
                || normalized.startsWith("o4");
    }

    static boolean supportsExplicitPromptCaching(String model) {
        String normalized = Objects.toString(model, "").trim().toLowerCase();
        return normalized.startsWith("gpt-5.6");
    }

    static List<ResponseInputItem> buildInputItems(ApplicationParam param, String input) {
        ResponseInputItem.Message.Builder message = ResponseInputItem.Message.builder()
                .role(ResponseInputItem.Message.Role.USER);
        String cacheableInput = Objects.toString(param.cacheableInput(), "");
        if (!cacheableInput.isBlank()) {
            ResponseInputText.Builder cacheableBlock = ResponseInputText.builder().text(cacheableInput);
            if (param.explicitPromptCaching() && supportsExplicitPromptCaching(param.model())) {
                cacheableBlock.putAdditionalProperty("prompt_cache_breakpoint", JsonValue.from(Map.of(
                        "mode", "explicit"
                )));
            }
            message.addContent(cacheableBlock.build());
        }
        message.addInputTextContent(Objects.toString(input, ""));
        for (String fileUrl : normalizeFileUrls(param)) {
            message.addContent(ResponseInputFile.builder()
                    .fileUrl(fileUrl)
                    .build());
        }
        for (ApplicationInputImage inputImage : normalizeInputImages(param)) {
            String label = Objects.toString(inputImage.label(), "").trim();
            if (!label.isBlank()) {
                message.addInputTextContent(label);
            }
            message.addContent(ResponseInputImage.builder()
                    .imageUrl(inputImage.imageUrl())
                    .detail(resolveImageDetail(param, inputImage))
                    .build());
        }
        return List.of(ResponseInputItem.ofMessage(message.build()));
    }

    static List<Tool> buildTools(ApplicationParam param) {
        if (param == null || !param.webSearchEnabled()) {
            return List.of();
        }
        ArrayList<Tool> tools = new ArrayList<>();
        WebSearchTool.Builder wsb = WebSearchTool.builder()
                .type(WebSearchTool.Type.WEB_SEARCH);
        String ctxSize = Objects.toString(param.webSearchContextSize(), "medium")
                .trim()
                .toLowerCase();
        switch (ctxSize) {
            case "low" -> wsb.searchContextSize(WebSearchTool.SearchContextSize.LOW);
            case "high" -> wsb.searchContextSize(WebSearchTool.SearchContextSize.HIGH);
            default -> wsb.searchContextSize(WebSearchTool.SearchContextSize.MEDIUM);
        }
        tools.add(Tool.ofWebSearch(wsb.build()));
        return List.copyOf(tools);
    }

    private static List<String> normalizeFileUrls(ApplicationParam param) {
        return param == null ? List.of() : normalizeUrls(param.fileUrls());
    }

    private static List<String> normalizeImageUrls(ApplicationParam param) {
        return param == null ? List.of() : normalizeUrls(param.imageUrls());
    }

    private static List<ApplicationInputImage> normalizeInputImages(ApplicationParam param) {
        if (param == null) {
            return List.of();
        }
        List<ApplicationInputImage> inputImages = param.inputImages();
        if (inputImages == null || inputImages.isEmpty()) {
            String defaultDetail = AiOptionReader.stringOpt(param, "model.imageDetail", null);
            return normalizeImageUrls(param).stream()
                    .map(url -> new ApplicationInputImage(url, "", defaultDetail))
                    .toList();
        }
        Set<String> seen = new LinkedHashSet<>();
        ArrayList<ApplicationInputImage> out = new ArrayList<>();
        for (ApplicationInputImage image : inputImages) {
            if (image == null) {
                continue;
            }
            String imageUrl = Objects.toString(image.imageUrl(), "").trim();
            if (imageUrl.isBlank() || !seen.add(imageUrl)) {
                continue;
            }
            out.add(new ApplicationInputImage(
                    imageUrl,
                    Objects.toString(image.label(), "").trim(),
                    image.detail()
            ));
        }
        return List.copyOf(out);
    }

    private static ResponseInputImage.Detail resolveImageDetail(ApplicationParam param, ApplicationInputImage inputImage) {
        String detail = inputImage == null ? null : inputImage.detail();
        if (detail == null || detail.isBlank()) {
            detail = AiOptionReader.stringOpt(param, "model.imageDetail", null);
        }
        if (detail == null || detail.isBlank()) {
            return ResponseInputImage.Detail.AUTO;
        }
        return switch (detail.trim().toLowerCase()) {
            case "high" -> ResponseInputImage.Detail.HIGH;
            case "low" -> ResponseInputImage.Detail.LOW;
            default -> ResponseInputImage.Detail.AUTO;
        };
    }

    private static List<String> normalizeUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String value : urls) {
            String normalized = Objects.toString(value, "").trim();
            if (!normalized.isBlank()) {
                seen.add(normalized);
            }
        }
        return List.copyOf(seen);
    }

    private static void trySetDouble(Object builder, String method, double v) {
        try {
            builder.getClass().getMethod(method, Double.TYPE).invoke(builder, v);
        } catch (Exception ignore) {
        }
    }

    private static void trySetLong(Object builder, String method, long v) {
        try {
            builder.getClass().getMethod(method, Long.TYPE).invoke(builder, v);
        } catch (Exception ignore) {
        }
    }

    private static ResponseFormatTextJsonSchemaConfig.Schema schemaFromMap(Map<String, Object> schemaMap) {
        // SDK 的 Schema 是“任意 JSON object”的载体：通过 additionalProperties 传入
        ResponseFormatTextJsonSchemaConfig.Schema.Builder sb = ResponseFormatTextJsonSchemaConfig.Schema.builder();
        for (var e : schemaMap.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) continue;
            sb.putAdditionalProperty(e.getKey(), JsonValue.from(e.getValue()));
        }
        return sb.build();
    }

    private static PromptCacheUsage promptCacheUsage(Response response) {
        if (response == null || response.usage().isEmpty()) {
            return PromptCacheUsage.empty();
        }
        var usage = response.usage().orElseThrow();
        var inputDetails = usage.inputTokensDetails();
        long cacheWriteTokens = additionalLong(inputDetails._additionalProperties(), "cache_write_tokens");
        return new PromptCacheUsage(
                usage.inputTokens(),
                inputDetails.cachedTokens(),
                cacheWriteTokens,
                usage.outputTokens()
        );
    }

    private static long additionalLong(Map<String, JsonValue> values, String key) {
        if (values == null || values.get(key) == null) {
            return 0L;
        }
        try {
            Long value = values.get(key).convert(Long.class);
            return value == null ? 0L : Math.max(0L, value);
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static void logPromptCacheUsage(String mode,
                                            ApplicationParam param,
                                            String model,
                                            PromptCacheUsage usage) {
        String promptCacheKey = Objects.toString(param.promptCacheKey(), "").trim();
        if (promptCacheKey.isBlank()) {
            return;
        }
        String cacheMode = param.explicitPromptCaching() && supportsExplicitPromptCaching(model)
                ? "explicit"
                : "implicit";
        log.info("openai prompt cache usage: mode={}, model={}, cacheMode={}, promptCacheKey={}, inputTokens={}, cachedTokens={}, cacheWriteTokens={}, outputTokens={}",
                mode,
                model,
                cacheMode,
                promptCacheKey,
                usage.inputTokens(),
                usage.cachedTokens(),
                usage.cacheWriteTokens(),
                usage.outputTokens());
    }

    private record PromptCacheUsage(long inputTokens,
                                    long cachedTokens,
                                    long cacheWriteTokens,
                                    long outputTokens) {

        private static PromptCacheUsage empty() {
            return new PromptCacheUsage(0L, 0L, 0L, 0L);
        }
    }

    private static List<String> concatModels(String primary, List<String> fallback) {
        String p = Objects.toString(primary, "").trim();
        if (p.isBlank()) return fallback;
        var out = new java.util.ArrayList<String>(1 + fallback.size());
        out.add(p);
        for (String m : fallback) {
            String t = Objects.toString(m, "").trim();
            if (!t.isBlank() && !t.equals(p)) out.add(t);
        }
        return List.copyOf(out);
    }

    private static long elapsedMillis(long startNs) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String summarizeThrowable(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 4) {
            if (depth > 0) {
                sb.append(" <- ");
            }
            sb.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                sb.append(": ").append(message.trim());
            }
            current = current.getCause();
            depth++;
        }
        String summary = sb.toString();
        if (summary.length() <= 400) {
            return summary;
        }
        return summary.substring(0, 400) + "...";
    }
}
