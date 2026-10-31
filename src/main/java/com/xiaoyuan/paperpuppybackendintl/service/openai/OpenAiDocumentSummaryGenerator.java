package com.xiaoyuan.paperpuppybackendintl.service.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.api.chatgpt.ApplicationParam;
import com.xiaoyuan.paperpuppybackendintl.api.chatgpt.OpenAiApplicationExecutor;
import com.xiaoyuan.paperpuppybackendintl.config.openai.OpenAiDocumentSummaryProperties;
import com.xiaoyuan.paperpuppybackendintl.config.openai.OpenAiProperties;
import com.xiaoyuan.paperpuppybackendintl.utils.ai.DocumentSummaryAiUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成文档摘要（单次、非流式、无状态）。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OpenAiDocumentSummaryGenerator {
    @Qualifier("jackson2Mapper")
    private final ObjectMapper objectMapper;
    private final OpenAiProperties openAiProperties;
    private final OpenAiDocumentSummaryProperties props;

    private final OpenAiApplicationExecutor executor = new OpenAiApplicationExecutor();

    public String generateSummary(String title, String fullText) {
        if (!props.enabled()) {
            log.debug("OpenAI document summary generator disabled");
            return null;
        }
        String apiKey = resolveApiKey(openAiProperties);
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenAI document summary generator missing apiKey (openai.api-key / OPENAI_API_KEY)");
            return null;
        }

        String model = (props.model() != null && !props.model().isBlank())
                ? props.model()
                : (openAiProperties.model() != null && !openAiProperties.model().isBlank()
                ? openAiProperties.model()
                : "gpt-4o-mini");

        String excerpt = buildExcerpt(fullText, props.maxInputChars());

        ApplicationParam param = ApplicationParam.builder()
                .apiKey(apiKey)
                .baseUrl(openAiProperties.baseUrl())
                .orgId(openAiProperties.orgId())
                .projectId(openAiProperties.projectId())
                .serviceTier(openAiProperties.serviceTier())
                .model(model)
                .timeoutMs(props.timeoutMs())
                .clientMaxRetries(0)
                .instructions(DocumentSummaryAiUtils.buildInstructions())
                .input(DocumentSummaryAiUtils.buildInput(title, excerpt))
                .jsonSchema(structuredSummarySchema())
                .jsonSchemaName("document_summary")
                .jsonSchemaStrict(true)
                .options(Map.of("model.fallbackModels", props.fallbackModels()))
                .build();

        try {
            String outputText = executor.call(param);
            String rawSummary = extractSummaryFromText(outputText);
            String finalSummary = DocumentSummaryAiUtils.sanitizeSummary(rawSummary, props.maxSummaryLength());
            log.info("OpenAI document summary ok: titleLen={}, inputLen={}, summaryLen={}",
                    title == null ? 0 : title.length(),
                    excerpt == null ? 0 : excerpt.length(),
                    finalSummary == null ? 0 : finalSummary.length());
            return finalSummary;
        } catch (Exception e) {
            log.warn("OpenAI document summary error: {}", e.getMessage());
            return null;
        }
    }

    private String extractSummaryFromText(String outputText) {
        if (outputText == null || outputText.isBlank()) return null;
        String s = tryParseSummaryJson(outputText);
        return s != null ? s : outputText;
    }

    private String tryParseSummaryJson(String maybeJson) {
        if (maybeJson == null) return null;
        String s = maybeJson.trim();
        if (s.isEmpty()) return null;
        if (!(s.startsWith("{") && s.endsWith("}"))) return null;
        try {
            JsonNode n = objectMapper.readTree(s);
            JsonNode summary = n.get("summary");
            return (summary != null && summary.isTextual()) ? summary.asText() : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Map<String, Object> structuredSummarySchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> summaryProp = new LinkedHashMap<>();
        summaryProp.put("type", "string");
        summaryProp.put("description", "简短的文档摘要，适合用于文件列表快速扫读");
        props.put("summary", summaryProp);
        schema.put("properties", props);
        schema.put("required", List.of("summary"));
        return schema;
    }

    private static String buildExcerpt(String fullText, int maxChars) {
        if (fullText == null || fullText.isBlank()) return "";
        String t = fullText.trim();
        if (maxChars <= 0 || t.length() <= maxChars) return t;
        // 取头尾，避免仅摘要到目录/前言
        int head = Math.max(10_000, (int) (maxChars * 0.7));
        head = Math.min(head, maxChars);
        int tail = Math.max(0, maxChars - head);
        String h = t.substring(0, Math.min(head, t.length()));
        String out;
        if (tail <= 0 || t.length() <= head) {
            out = h;
        } else {
            String r = t.substring(Math.max(t.length() - tail, 0));
            out = h + "\n\n---\n\n(… content truncated …)\n\n---\n\n" + r;
        }
        return out;
    }

    private static String resolveApiKey(OpenAiProperties props) {
        if (props != null && props.apiKey() != null && !props.apiKey().isBlank()) {
            return props.apiKey();
        }
        return System.getenv("OPENAI_API_KEY");
    }
}

