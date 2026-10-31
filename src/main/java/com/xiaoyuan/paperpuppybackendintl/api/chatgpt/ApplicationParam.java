package com.xiaoyuan.paperpuppybackendintl.api.chatgpt;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * @author Seacolour
 * @date 2025/12/30 11:27
 * @description:
 */
@Builder(toBuilder = true)
@Getter
@Accessors(fluent = true)
public class ApplicationParam implements AiParam {
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final String orgId;
    private final String projectId;
    private final String serviceTier;

    /**
     * OpenAI SDK client 超时（毫秒）。&lt;=0 使用默认值。
     */
    @Builder.Default
    private final long timeoutMs = -1;

    /**
     * OpenAI SDK client 重试次数（&lt;0 使用默认；0 关闭；&gt;0 启用）。
     */
    @Builder.Default
    private final int clientMaxRetries = -1;

    /**
     * 类似“应用预设”：放系统/开发者指令（例如：返回用户意图 + 文本总结 JSON）。
     */
    private final String instructions;

    /**
     * 本次输入文本。
     */
    private final String input;

    /**
     * 可选：位于动态 input 之前的稳定输入块。用于复用较大的静态 prompt 前缀。
     */
    private final String cacheableInput;

    /**
     * 可选：OpenAI prompt_cache_key，用于把相同公共前缀稳定路由到同一缓存分区。
     */
    private final String promptCacheKey;

    /**
     * 是否对 GPT-5.6+ 仅缓存 cacheableInput，并关闭最新消息上的隐式断点。
     */
    @Builder.Default
    private final boolean explicitPromptCaching = false;

    /**
     * 可选：随用户消息通过 Responses API input_file 传入的文件 URL。
     */
    @Builder.Default
    private final List<String> fileUrls = List.of();

    /**
     * 可选：随用户消息通过 Responses API input_image 传入的图片 URL。
     */
    @Builder.Default
    private final List<String> imageUrls = List.of();

    /**
     * 可选：带文本标签的图片输入。用于让模型把图片视觉内容和外部 URL/文件名等元数据对齐。
     */
    @Builder.Default
    private final List<ApplicationInputImage> inputImages = List.of();

    /**
     * 可选：Structured Outputs（Responses API 的 text.format json_schema）。
     *
     * <p>示例（JSON Schema Root Object）：</p>
     * <pre>
     * {
     *   "type": "object",
     *   "properties": { "title": { "type": "string" } },
     *   "required": ["title"],
     *   "additionalProperties": false
     * }
     * </pre>
     *
     * <p>不设置则走普通文本输出。</p>
     */
    private final Map<String, Object> jsonSchema;

    /**
     * Structured Outputs 的 schema 名称（默认 "output"）。
     */
    @Builder.Default
    private final String jsonSchemaName = "output";

    /**
     * Structured Outputs 严格模式（默认 true）。
     */
    @Builder.Default
    private final boolean jsonSchemaStrict = true;

    /**
     * 可选：schema 描述（用于提升可读性/可调试性）。
     */
    private final String jsonSchemaDescription;

    /**
     * 是否为本次 Responses API 请求启用 OpenAI 内置 web_search 工具。
     */
    @Builder.Default
    private final boolean webSearchEnabled = false;

    /**
     * web_search 注入模型的搜索上下文大小：low / medium / high。
     */
    @Builder.Default
    private final String webSearchContextSize = "medium";

    @Builder.Default
    private final Map<String, Object> options = Map.of();
}
