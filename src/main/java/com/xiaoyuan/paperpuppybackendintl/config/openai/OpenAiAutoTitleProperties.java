package com.xiaoyuan.paperpuppybackendintl.config.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 自动生成会话标题配置（首轮对话完成后自动改 title）。
 *
 * <p>注意：这里是“功能开关/模型/占位符标题”的配置；API Key 仍由 {@link OpenAiProperties} 管理。</p>
 */
@ConfigurationProperties(prefix = "openai.auto-title")
public record OpenAiAutoTitleProperties(
        boolean enabled,
        /**
         * 用于生成标题的模型名。建议用轻量模型（成本低、延迟低）。
         */
        String model,
        /**
         * 单次调用超时（毫秒）。应显著小于正常聊天超时，避免影响主流程。
         */
        long timeoutMs,
        /**
         * 只有当会话 title 落在这些“占位符标题”中时，才会被自动改写（避免覆盖用户手动修改）。
         */
        List<String> placeholderTitles,
        /**
         * 允许的最大标题长度（字符数）。超出会被截断。
         */
        int maxTitleLength
) {
    public OpenAiAutoTitleProperties {
        if (timeoutMs <= 0) timeoutMs = 4_000;
        if (maxTitleLength <= 0) maxTitleLength = 60;
        if (placeholderTitles == null || placeholderTitles.isEmpty()) {
            placeholderTitles = List.of("新会话", "新对话", "New chat", "New session", "Chat");
        }
    }
}


