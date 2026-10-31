package com.xiaoyuan.paperpuppybackendintl.api.chatgpt;

import java.util.Map;

/**
 * @author Seacolour
 * @date 2025/12/30 11:27
 * @description:
 */
public interface AiParam {
    String apiKey();
    String model();
    /**
     * 可选项（建议只放“非必填、且 SDK Builder 有对应字段”的参数）。
     *
     * 约定键（本项目轻量封装会读取）：
     * - model.fallbackModels: List&lt;String&gt;（失败后自动切换模型；按顺序尝试）
     * - model.serviceTier: String（覆盖默认的 OpenAI service_tier）
     * - model.reasoningEffort: String（Responses API reasoning.effort）
     * - model.textVerbosity: String（Responses API text.verbosity）
     * - model.maxOutputTokens: Integer（Responses API max_output_tokens）
     *
     * 透传键（尽力映射到 OpenAI SDK Builder）：
     * - model.temperature: Double
     * - model.topP: Double
     * - maxTokens: Integer（chat completions）
     */
    Map<String, Object> options(); // temperature / topP / maxTokens / retry configs

    /**
     * HTTP 客户端超时（毫秒）。&lt;=0 表示使用 SDK 默认值。
     *
     * <p>对应 openai-java 的 {@code OpenAIOkHttpClient.builder().timeout(Duration)}。</p>
     */
    default long timeoutMs() {
        return -1;
    }

    /**
     * SDK 级别重试次数。&lt;0 表示使用 SDK 默认；0 表示关闭；&gt;0 表示启用。
     *
     * <p>对应 openai-java 的 {@code OpenAIOkHttpClient.builder().maxRetries(int)}。</p>
     */
    default int clientMaxRetries() {
        return -1;
    }

    /**
     * 可选：自定义 baseUrl（兼容私有化/代理），不传则使用官方默认值。
     * 注意：openai-java 需要 baseUrl 指向 API 根路径（含 /v1）。
     */
    default String baseUrl() {
        return null;
    }

    default String orgId() {
        return null;
    }

    default String projectId() {
        return null;
    }

    /**
     * OpenAI service_tier（例如 priority / default / flex）。
     */
    default String serviceTier() {
        return null;
    }
}

