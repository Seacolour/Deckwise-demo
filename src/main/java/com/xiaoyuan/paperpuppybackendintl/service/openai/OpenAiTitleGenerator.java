package com.xiaoyuan.paperpuppybackendintl.service.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.api.chatgpt.ApplicationParam;
import com.xiaoyuan.paperpuppybackendintl.api.chatgpt.OpenAiApplicationExecutor;
import com.xiaoyuan.paperpuppybackendintl.config.openai.OpenAiAutoTitleProperties;
import com.xiaoyuan.paperpuppybackendintl.config.openai.OpenAiProperties;
import com.xiaoyuan.paperpuppybackendintl.utils.ai.TitleAiUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 生成会话标题（单次、非流式、无状态）。
 *
 * <p>使用 OpenAI 官方推荐的 Responses API：POST /v1/responses</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OpenAiTitleGenerator {
    @Qualifier("jackson2Mapper")
    private final ObjectMapper objectMapper;
    private final OpenAiProperties openAiProperties;
    private final OpenAiAutoTitleProperties autoTitleProperties;

    private final OpenAiApplicationExecutor executor = new OpenAiApplicationExecutor();

    public String generateTitle(String userText) {
        return generateTitleInternal(
                TitleAiUtils.buildInstructions(),
                TitleAiUtils.buildInputFromUserOnly(userText),
                "auto_title",
                "简短、准确的会话标题",
                "OpenAI title");
    }

    private String generateTitleInternal(String instructions,
                                         String input,
                                         String schemaName,
                                         String titleDescription,
                                         String logLabel) {
        if (!autoTitleProperties.enabled()) {
            log.debug("OpenAI title generator disabled");
            return null;
        }
        String apiKey = resolveApiKey(openAiProperties);
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenAI title generator missing apiKey (openai.api-key / OPENAI_API_KEY)");
            return null;
        }

        String model = (autoTitleProperties.model() != null && !autoTitleProperties.model().isBlank())
                ? autoTitleProperties.model()
                : (openAiProperties.model() != null && !openAiProperties.model().isBlank() ? openAiProperties.model() : "gpt-4o-mini");

        ApplicationParam param = ApplicationParam.builder()
                .apiKey(apiKey)
                .baseUrl(openAiProperties.baseUrl())
                .orgId(openAiProperties.orgId())
                .projectId(openAiProperties.projectId())
                .serviceTier(openAiProperties.serviceTier())
                .model(model)
                .timeoutMs(autoTitleProperties.timeoutMs())
                .clientMaxRetries(0) // 标题生成不建议重试太多，避免阻塞；如需可改为配置化
                .instructions(instructions)
                .input(input)
                // Structured Outputs：强约束输出为 {"title": "..."}，避免自由文本不稳定
                .jsonSchema(structuredTitleSchema(titleDescription))
                .jsonSchemaName(schemaName)
                .jsonSchemaStrict(true)
                .build();

        try {
            String outputText = executor.call(param);
            String rawTitle = extractTitleFromText(outputText);
            String finalTitle = TitleAiUtils.sanitizeTitle(rawTitle, autoTitleProperties.maxTitleLength());
            log.info("{} call ok: rawTitle={}, finalTitle={}",
                    logLabel,
                    safeTruncate(String.valueOf(rawTitle), 80), safeTruncate(String.valueOf(finalTitle), 80));
            return finalTitle;
        } catch (Exception e) {
            log.warn("{} call error: {}", logLabel, e.getMessage());
            return null;
        }
    }

    /**
     * 仅用用户首条消息生成标题（推荐：与 AI 回复并行）。
     */
    public String generateTitleFromUserMessage(String userText) {
        return generateTitle(userText);
    }

    /**
     * 优先按 Structured Outputs 解析 title；失败时回退到 output_text / output[] 里的文本提取。
     */
    private String extractTitleFromText(String outputText) {
        if (outputText == null || outputText.isBlank()) return null;
        String t = tryParseTitleJson(outputText);
        return t != null ? t : outputText;
    }

    private String tryParseTitleJson(String maybeJson) {
        if (maybeJson == null) return null;
        String s = maybeJson.trim();
        if (s.isEmpty()) return null;
        if (!(s.startsWith("{") && s.endsWith("}"))) {
            return null;
        }
        try {
            JsonNode n = objectMapper.readTree(s);
            JsonNode title = n.get("title");
            return (title != null && title.isTextual()) ? title.asText() : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Map<String, Object> structuredTitleSchema(String titleDescription) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> titleProp = new LinkedHashMap<>();
        titleProp.put("type", "string");
        titleProp.put("description", titleDescription);
        props.put("title", titleProp);
        schema.put("properties", props);
        schema.put("required", List.of("title"));
        return schema;
    }

    private static String resolveApiKey(OpenAiProperties props) {
        if (props != null && props.apiKey() != null && !props.apiKey().isBlank()) {
            return props.apiKey();
        }
        return System.getenv("OPENAI_API_KEY");
    }

    private static String safeTruncate(String s, int max) {
        if (s == null) return "";
        if (max <= 0 || s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }
}


