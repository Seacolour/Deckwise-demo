package com.xiaoyuan.paperpuppyagent.config;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI 配置（建议通过环境变量或外部配置注入，不要写死在仓库里）。
 */
@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String apiKey,
        String baseUrl,
        String model,
        String orgId,
        String projectId
) {
}

