package com.xiaoyuan.paperpuppybackendintl.config.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 文档摘要（Markdown）自动生成配置：后端异步生成 summary，并写回 documents.summary + documents.summary_version。
 *
 * <p>注意：这是“功能开关/模型/输入输出限制/队列配置”的配置；API Key 仍由 {@link OpenAiProperties} 管理。</p>
 */
@ConfigurationProperties(prefix = "openai.document-summary")
public record OpenAiDocumentSummaryProperties(
        boolean enabled,
        /**
         * 用于生成摘要的模型名。建议用轻量模型（成本低、延迟低）。
         */
        String model,
        /**
         * 单次调用超时（毫秒）。应明显小于主对话超时，避免阻塞 worker。
         */
        long timeoutMs,
        /**
         * 允许的最大摘要长度（字符数）。超出会被截断。
         */
        int maxSummaryLength,
        /**
         * 发送给模型的最大输入字符数（从文档头尾截取）。
         */
        int maxInputChars,
        /**
         * 任务最大重试次数（超过则放弃，避免无限重试）。
         */
        int maxAttempts,
        /**
         * OpenAI 调用失败时的 fallback models（顺序尝试）。
         */
        List<String> fallbackModels
) {
    public OpenAiDocumentSummaryProperties {
        if (timeoutMs <= 0) timeoutMs = 12_000;
        if (maxSummaryLength <= 0) maxSummaryLength = 600;
        if (maxInputChars <= 0) maxInputChars = 60_000;
        if (maxAttempts <= 0) maxAttempts = 3;
        if (fallbackModels == null) fallbackModels = List.of();
    }
}

