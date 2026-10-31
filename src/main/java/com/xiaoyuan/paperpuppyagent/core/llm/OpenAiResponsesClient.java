package com.xiaoyuan.paperpuppyagent.core.llm;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.RateLimitException;
import com.openai.helpers.ResponseAccumulator;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseUsage;
import com.openai.models.responses.Tool;
import com.xiaoyuan.paperpuppyagent.config.AgentOpenAiContextManagementProperties;
import com.xiaoyuan.paperpuppyagent.config.AgentModelCallProperties;
import com.xiaoyuan.paperpuppyagent.config.OpenAiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Responses-compatible model client (OpenAI SDK transport).
 */
@Slf4j
@Component
public class OpenAiResponsesClient {

    private static final Pattern TRY_AGAIN_MS = Pattern.compile("try again in\\s+(\\d+)\\s*ms", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRY_AGAIN_SECONDS = Pattern.compile("try again in\\s+([0-9.]+)s", Pattern.CASE_INSENSITIVE);

    private final OpenAiProperties props;
    private final AgentModelCallProperties modelCallProperties;
    private final AgentOpenAiContextManagementProperties contextManagementProperties;
    private final AgentModelCapacityLimiter capacityLimiter;
    private final ModelEndpointRegistry modelEndpointRegistry;
    private volatile OpenAIClient client;

    public OpenAiResponsesClient(OpenAiProperties props,
                                 AgentModelCallProperties modelCallProperties,
                                 AgentOpenAiContextManagementProperties contextManagementProperties,
                                 AgentModelCapacityLimiter capacityLimiter,
                                 ModelEndpointRegistry modelEndpointRegistry) {
        this.props = props;
        this.modelCallProperties = modelCallProperties;
        this.contextManagementProperties = contextManagementProperties;
        this.capacityLimiter = capacityLimiter;
        this.modelEndpointRegistry = modelEndpointRegistry;
    }

    public Response createResponse(String model,
                                  List<ResponseInputItem> input,
                                  List<Tool> tools,
                                  String instructions,
                                  boolean enablePriorityProcessing) {
        return createResponse(model, input, tools, instructions, enablePriorityProcessing,
                modelCallProperties.getDefaultMaxOutputTokens(), "sync");
    }

    public Response createResponse(String model,
                                  List<ResponseInputItem> input,
                                  List<Tool> tools,
                                  String instructions,
                                  boolean enablePriorityProcessing,
                                  long maxOutputTokens,
                                  String callSite) {
        return createResponse(modelEndpointRegistry.resolve(model, "", ""), input, tools, instructions,
                enablePriorityProcessing, maxOutputTokens, callSite);
    }

    public Response createResponse(ModelRequestContext modelContext,
                                  List<ResponseInputItem> input,
                                  List<Tool> tools,
                                  String instructions,
                                  boolean enablePriorityProcessing,
                                  long maxOutputTokens,
                                  String callSite) {
        OpenAIClient c = client(modelContext);
        String m = modelContext.upstreamModel();
        boolean priority = enablePriorityProcessing && modelContext.endpoint().supportsPriorityProcessing();

        long startedNs = System.nanoTime();
        int attempt = 0;
        try {
        while (true) {
            ResponseCreateParams params = buildCreateParams(
                    modelContext, input, tools, instructions, priority, maxOutputTokens);
            AgentModelCapacityLimiter.Reservation reservation = reserveCapacity(
                    modelContext.providerId(), callSite, m, input, tools, instructions,
                    params.maxOutputTokens().orElse(null), priority, attempt, null);
            RuntimeException retryableFailure = null;
            Long actualTotalTokens = null;
            boolean success = false;
            String errorType = "";
            logRequestStart(modelContext.providerId(), callSite, m, input, tools, instructions, params.maxOutputTokens().orElse(null),
                    priority, attempt);
            try {
                Response response = c.responses().create(params);
                actualTotalTokens = actualTotalTokens(response);
                success = true;
                logResponseDone(modelContext.providerId(), callSite, m, response, startedNs, attempt, params.maxOutputTokens().orElse(null));
                return response;
            } catch (RuntimeException e) {
                errorType = e.getClass().getSimpleName();
                if (!shouldRetry(e) || attempt >= maxRetries()) {
                    logRequestFailed(modelContext.providerId(), callSite, m, e, startedNs, attempt);
                    throw sanitizedFailure(modelContext.providerId(), e);
                }
                retryableFailure = e;
            } finally {
                releaseCapacity(reservation, actualTotalTokens, success, errorType);
            }
            sleepBeforeRetry(modelContext.providerId(), callSite, m, retryableFailure, attempt);
            attempt++;
        }
        } finally {
            closeRequestClient(modelContext, c);
        }
    }

    /**
     * Responses API（Streaming）：边消费 output_text delta，边用 ResponseAccumulator 累积最终 Response。
     *
     * <p>用途：实现 gRPC 的流式 TextChunk，同时仍然保留完整 Response 用于后续 tool_call 判断。</p>
     */
    public Response createResponseStreaming(String model,
                                           List<ResponseInputItem> input,
                                           List<Tool> tools,
                                           String instructions,
                                           boolean enablePriorityProcessing,
                                           Consumer<String> onTextDelta,
                                           BooleanSupplier cancelled) {
        return createResponseStreaming(model, input, tools, instructions, enablePriorityProcessing,
                modelCallProperties.getDefaultMaxOutputTokens(), "stream", onTextDelta, cancelled);
    }

    public Response createResponseStreaming(String model,
                                           List<ResponseInputItem> input,
                                           List<Tool> tools,
                                           String instructions,
                                           boolean enablePriorityProcessing,
                                           long maxOutputTokens,
                                           String callSite,
                                           Consumer<String> onTextDelta,
                                           BooleanSupplier cancelled) {
        return createResponseStreaming(modelEndpointRegistry.resolve(model, "", ""), input, tools,
                instructions, enablePriorityProcessing, maxOutputTokens, callSite, onTextDelta, cancelled);
    }

    public Response createResponseStreaming(ModelRequestContext modelContext,
                                           List<ResponseInputItem> input,
                                           List<Tool> tools,
                                           String instructions,
                                           boolean enablePriorityProcessing,
                                           long maxOutputTokens,
                                           String callSite,
                                           Consumer<String> onTextDelta,
                                           BooleanSupplier cancelled) {
        OpenAIClient c = client(modelContext);
        String m = modelContext.upstreamModel();
        boolean priority = enablePriorityProcessing && modelContext.endpoint().supportsPriorityProcessing();

        long startedNs = System.nanoTime();
        int attempt = 0;
        try {
        while (true) {
            boolean[] emittedText = {false};
            ResponseCreateParams params = buildCreateParams(
                    modelContext, input, tools, instructions, priority, maxOutputTokens);
            AgentModelCapacityLimiter.Reservation reservation = reserveCapacity(
                    modelContext.providerId(), callSite, m, input, tools, instructions,
                    params.maxOutputTokens().orElse(null), priority, attempt, cancelled);
            RuntimeException retryableFailure = null;
            Long actualTotalTokens = null;
            boolean success = false;
            String errorType = "";
            logRequestStart(modelContext.providerId(), callSite, m, input, tools, instructions, params.maxOutputTokens().orElse(null),
                    priority, attempt);
            try {
                Response response = executeStreaming(c, params, onTextDelta, cancelled, emittedText);
                if (response == null) return null;
                actualTotalTokens = actualTotalTokens(response);
                success = true;
                logResponseDone(modelContext.providerId(), callSite, m, response, startedNs, attempt, params.maxOutputTokens().orElse(null));
                return response;
            } catch (RuntimeException e) {
                errorType = e.getClass().getSimpleName();
                if (cancelled(cancelled)) return null;
                if (emittedText[0] || !shouldRetry(e) || attempt >= maxRetries()) {
                    logRequestFailed(modelContext.providerId(), callSite, m, e, startedNs, attempt);
                    throw sanitizedFailure(modelContext.providerId(), e);
                }
                retryableFailure = e;
            } finally {
                releaseCapacity(reservation, actualTotalTokens, success, errorType);
            }
            sleepBeforeRetry(modelContext.providerId(), callSite, m, retryableFailure, attempt);
            attempt++;
        }
        } finally {
            closeRequestClient(modelContext, c);
        }
    }

    ResponseCreateParams buildCreateParams(ModelRequestContext modelContext,
                                                   List<ResponseInputItem> input,
                                                   List<Tool> tools,
                                                   String instructions,
                                                   boolean enablePriorityProcessing,
                                                   long maxOutputTokens) {
        ResponseCreateParams.Builder b = ResponseCreateParams.builder()
                .model(modelContext.upstreamModel())
                .inputOfResponse(input == null ? List.of() : input);

        String ins = Objects.toString(instructions, "").trim();
        if (!ins.isBlank()) b.instructions(ins);
        if (maxOutputTokens > 0) b.maxOutputTokens(maxOutputTokens);
        if (enablePriorityProcessing && modelContext.endpoint().supportsPriorityProcessing()) {
            b.serviceTier(ResponseCreateParams.ServiceTier.PRIORITY);
        }
        if (contextManagementProperties.isEnabled() && modelContext.endpoint().supportsContextManagement()) {
            ResponseCreateParams.ContextManagement.Builder contextManagement =
                    ResponseCreateParams.ContextManagement.builder().type("compaction");
            if (contextManagementProperties.getCompactThreshold() > 0) {
                contextManagement.compactThreshold(contextManagementProperties.getCompactThreshold());
            }
            b.store(contextManagementProperties.isStore());
            b.addContextManagement(contextManagement.build());
        }

        if (tools != null && !tools.isEmpty()) {
            if (!modelContext.endpoint().supportsTools()) {
                throw new IllegalArgumentException("Selected model provider does not support Agent tools in this version");
            }
            b.tools(tools);
            b.parallelToolCalls(modelCallProperties.isParallelToolCallsEnabled());
        }

        return b.build();
    }

    private Response executeStreaming(OpenAIClient c,
                                      ResponseCreateParams params,
                                      Consumer<String> onTextDelta,
                                      BooleanSupplier cancelled,
                                      boolean[] emittedText) {
        ResponseAccumulator acc = ResponseAccumulator.create();
        boolean cancelledEarly = false;
        try (StreamResponse<ResponseStreamEvent> stream = c.responses().createStreaming(params)) {
            Iterator<ResponseStreamEvent> it = stream.stream().iterator();
            while (it.hasNext()) {
                if (cancelled != null && cancelled.getAsBoolean()) {
                    cancelledEarly = true;
                    break;
                }
                ResponseStreamEvent ev = it.next();
                if (ev == null) continue;
                acc.accumulate(ev);
                ev.outputTextDelta().ifPresent(textEv -> {
                    String delta = Objects.toString(textEv.delta(), "");
                    if (delta.isEmpty()) return;
                    if (cancelled != null && cancelled.getAsBoolean()) return;
                    emittedText[0] = true;
                    if (onTextDelta != null) onTextDelta.accept(delta);
                });
            }
        }
        // 如果上层取消了，不强行构造最终 Response，避免不完整状态下抛异常
        if (cancelledEarly) return null;
        return acc.response();
    }

    private void logRequestStart(String providerId,
                                 String callSite,
                                 String model,
                                 List<ResponseInputItem> input,
                                 List<Tool> tools,
                                 String instructions,
                                 Long maxOutputTokens,
                                 boolean enablePriorityProcessing,
                                 int attempt) {
        log.info("[Model][Responses] request start: provider={}, callSite={}, model={}, inputItems={}, estimatedInputChars={}, instructionsChars={}, toolsCount={}, maxOutputTokens={}, priority={}, attempt={}",
                providerId, safeCallSite(callSite), model, input == null ? 0 : input.size(), estimateInputChars(input),
                Objects.toString(instructions, "").length(), tools == null ? 0 : tools.size(),
                maxOutputTokens == null ? "unset" : maxOutputTokens, enablePriorityProcessing, attempt + 1);
    }

    private void logResponseDone(String providerId,
                                 String callSite,
                                 String model,
                                 Response response,
                                 long startedNs,
                                 int attempt,
                                 Long maxOutputTokens) {
        long elapsedMs = (System.nanoTime() - startedNs) / 1_000_000;
        Optional<ResponseUsage> usage = response == null ? Optional.empty() : response.usage();
        if (usage.isPresent()) {
            ResponseUsage u = usage.get();
            log.info("[Model][Responses] request done: provider={}, callSite={}, model={}, responseId={}, inputTokens={}, outputTokens={}, totalTokens={}, serviceTier={}, elapsedMs={}, attempt={}",
                    providerId, safeCallSite(callSite), model, response.id(), u.inputTokens(), u.outputTokens(), u.totalTokens(),
                    response.serviceTier().map(serviceTier -> serviceTier.asString()).orElse(""), elapsedMs,
                    attempt + 1);
            if (maxOutputTokens != null && maxOutputTokens > 0 && u.outputTokens() >= maxOutputTokens) {
                log.warn("[Model][Responses] output token limit reached: provider={}, callSite={}, model={}, responseId={}, outputTokens={}, maxOutputTokens={}. Function call arguments may be truncated; retry with a smaller tool payload or raise the output cap.",
                        providerId, safeCallSite(callSite), model, response.id(), u.outputTokens(), maxOutputTokens);
            }
        } else {
            log.info("[Model][Responses] request done: provider={}, callSite={}, model={}, responseId={}, serviceTier={}, elapsedMs={}, attempt={}",
                    providerId, safeCallSite(callSite), model, response == null ? "" : response.id(),
                    response == null ? "" : response.serviceTier().map(serviceTier -> serviceTier.asString()).orElse(""),
                    elapsedMs, attempt + 1);
        }
    }

    private void logRequestFailed(String providerId, String callSite, String model,
                                  RuntimeException e, long startedNs, int attempt) {
        long elapsedMs = (System.nanoTime() - startedNs) / 1_000_000;
        log.warn("[Model][Responses] request failed: provider={}, callSite={}, model={}, errorType={}, statusCode={}, elapsedMs={}, attempt={}",
                providerId, safeCallSite(callSite), model, e.getClass().getSimpleName(), safeStatusCode(e), elapsedMs, attempt + 1);
    }

    private static long estimateInputChars(List<ResponseInputItem> input) {
        if (input == null || input.isEmpty()) return 0L;
        long total = 0L;
        for (ResponseInputItem item : input) {
            total += Objects.toString(item, "").length();
        }
        return total;
    }

    private static long estimateToolChars(List<Tool> tools) {
        if (tools == null || tools.isEmpty()) return 0L;
        long total = 0L;
        for (Tool tool : tools) {
            total += Objects.toString(tool, "").length();
        }
        return total;
    }

    private AgentModelCapacityLimiter.Reservation reserveCapacity(String providerId,
                                                                  String callSite,
                                                                  String model,
                                                                  List<ResponseInputItem> input,
                                                                  List<Tool> tools,
                                                                  String instructions,
                                                                  Long maxOutputTokens,
                                                                  boolean enablePriorityProcessing,
                                                                  int attempt,
                                                                  BooleanSupplier cancelled) {
        AgentModelCapacityLimiter.CapacityRequest request = new AgentModelCapacityLimiter.CapacityRequest(
                providerId,
                model,
                safeCallSite(callSite),
                estimateInputChars(input),
                Objects.toString(instructions, "").length(),
                estimateToolChars(tools),
                maxOutputTokens == null ? 0L : Math.max(maxOutputTokens, 0L),
                enablePriorityProcessing,
                attempt + 1);
        return capacityLimiter.acquire(request, cancelled);
    }

    private void releaseCapacity(AgentModelCapacityLimiter.Reservation reservation,
                                 Long actualTotalTokens,
                                 boolean success,
                                 String errorType) {
        capacityLimiter.release(reservation, actualTotalTokens, success, errorType);
    }

    private static Long actualTotalTokens(Response response) {
        Optional<ResponseUsage> usage = response == null ? Optional.empty() : response.usage();
        return usage.map(u -> Math.max(u.totalTokens(), 0L)).orElse(null);
    }

    private boolean shouldRetry(RuntimeException e) {
        if (e instanceof RateLimitException) return true;
        if (e instanceof OpenAIRetryableException) return true;
        return e instanceof OpenAIServiceException serviceException && serviceException.statusCode() == 429;
    }

    private void sleepBeforeRetry(String providerId, String callSite, String model,
                                  RuntimeException e, int attempt) {
        long waitMs = retryDelayMs(e, attempt);
        log.warn("[Model][Responses] retrying: provider={}, callSite={}, model={}, waitMs={}, attempt={}, maxRetries={}, errorType={}, statusCode={}",
                providerId, safeCallSite(callSite), model, waitMs, attempt + 1, maxRetries(),
                e.getClass().getSimpleName(), safeStatusCode(e));
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off model request", interrupted);
        }
    }

    private static int safeStatusCode(RuntimeException error) {
        return error instanceof OpenAIServiceException serviceException ? serviceException.statusCode() : 0;
    }

    static RuntimeException sanitizedFailure(String providerId, RuntimeException error) {
        // Provider exceptions may contain request/credential echoes. Do not propagate their raw
        // message or cause to gRPC, logs, or the persisted Agent run error field.
        return new IllegalStateException("Model request failed: provider=" + providerId
                + ", errorType=" + error.getClass().getSimpleName()
                + ", statusCode=" + safeStatusCode(error));
    }

    private long retryDelayMs(RuntimeException e, int attempt) {
        long retryAfterMs = retryAfterMs(e);
        long base = Math.max(modelCallProperties.getInitialBackoffMs(), 1L);
        long exponential = safeMultiply(base, 1L << Math.min(attempt, 10));
        long waitMs = retryAfterMs > 0 ? retryAfterMs : exponential;
        long maxBackoff = Math.max(modelCallProperties.getMaxBackoffMs(), base);
        waitMs = Math.min(Math.max(waitMs, base), maxBackoff);
        long jitter = Math.max(modelCallProperties.getJitterMs(), 0L);
        if (jitter > 0) waitMs += ThreadLocalRandom.current().nextLong(jitter + 1);
        return waitMs;
    }

    private long retryAfterMs(RuntimeException e) {
        if (e instanceof OpenAIServiceException serviceException) {
            long headerMs = parseRetryAfterMs(serviceException);
            if (headerMs > 0) return headerMs;
        }
        return parseRetryAfterFromMessage(Objects.toString(e.getMessage(), ""));
    }

    private static long parseRetryAfterMs(OpenAIServiceException e) {
        String retryAfterMs = firstHeader(e, "retry-after-ms");
        if (retryAfterMs != null) {
            Long parsed = parsePositiveLong(retryAfterMs);
            if (parsed != null) return parsed;
        }

        String retryAfter = firstHeader(e, "retry-after");
        if (retryAfter == null) return 0L;
        Long seconds = parsePositiveLong(retryAfter);
        if (seconds != null) return seconds * 1000L;

        try {
            long delay = Duration.between(Instant.now(), ZonedDateTime.parse(
                    retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis();
            return Math.max(delay, 0L);
        } catch (DateTimeParseException ignore) {
            return 0L;
        }
    }

    private static String firstHeader(OpenAIServiceException e, String name) {
        for (String actualName : e.headers().names()) {
            if (!actualName.equalsIgnoreCase(name)) continue;
            List<String> values = e.headers().values(actualName);
            if (values != null && !values.isEmpty()) return values.get(0);
        }
        return null;
    }

    private static Long parsePositiveLong(String value) {
        try {
            long parsed = Long.parseLong(Objects.toString(value, "").trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    private static long parseRetryAfterFromMessage(String message) {
        Matcher ms = TRY_AGAIN_MS.matcher(message);
        if (ms.find()) return Long.parseLong(ms.group(1));
        Matcher seconds = TRY_AGAIN_SECONDS.matcher(message);
        if (seconds.find()) {
            double value = Double.parseDouble(seconds.group(1));
            return Math.round(value * 1000D);
        }
        return 0L;
    }

    private int maxRetries() {
        return Math.max(modelCallProperties.getMaxRetries(), 0);
    }

    private static long safeMultiply(long value, long factor) {
        if (value <= 0 || factor <= 0) return 1L;
        if (value > Long.MAX_VALUE / factor) return Long.MAX_VALUE;
        return value * factor;
    }

    private static boolean cancelled(BooleanSupplier cancelled) {
        return cancelled != null && cancelled.getAsBoolean();
    }

    private static String safeCallSite(String callSite) {
        String s = Objects.toString(callSite, "").trim();
        return s.isBlank() ? "unknown" : s;
    }

    private OpenAIClient client() {
        OpenAIClient c = client;
        if (c != null) return c;
        synchronized (this) {
            c = client;
            if (c != null) return c;

            String apiKey = Objects.toString(props.apiKey(), "").trim();
            if (apiKey.isBlank()) {
                throw new IllegalStateException("OPENAI_API_KEY is blank");
            }

            OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                    .apiKey(apiKey)
                    .baseUrl(ModelEndpointRegistry.normalizeOpenAiBaseUrl(props.baseUrl()));

            // 你们当前 OpenAiProperties 里未必有 org/project，这里先不强依赖；需要再加字段即可
            client = builder.build();
            return client;
        }
    }

    private OpenAIClient client(ModelRequestContext modelContext) {
        try {
            if (modelContext.legacyOpenAiConfiguration()) {
                return client();
            }
            // BYOK credentials must not become cache keys or live beyond this request.
            return OpenAIOkHttpClient.builder()
                    .apiKey(modelContext.apiKey())
                    .baseUrl(modelContext.endpoint().baseUrl())
                    .build();
        } catch (RuntimeException error) {
            throw sanitizedFailure(modelContext.providerId(), error);
        }
    }

    private void closeRequestClient(ModelRequestContext modelContext, OpenAIClient requestClient) {
        if (modelContext.legacyOpenAiConfiguration()) return;
        try {
            requestClient.close();
        } catch (RuntimeException error) {
            log.warn("[Model][Responses] client close failed: provider={}, errorType={}",
                    modelContext.providerId(), error.getClass().getSimpleName());
        }
    }

}
