package com.xiaoyuan.paperpuppybackendintl.api.chatgpt;

import com.openai.models.chat.completions.ChatCompletionCreateParams;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * @author Seacolour
 * @date 2025/12/30 11:28
 * @description:
 */
@Builder(toBuilder = true)
@Getter
@Accessors(fluent = true)
public class GenerationParam implements AiParam {
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
     * Chat Completions 消息列表（多轮上下文）。
     * 说明：这里直接使用 openai-java SDK 的 message param 类型，调用侧可自由构造 system/user/assistant 消息。
     */
    @Builder.Default
    private final List<com.openai.models.chat.completions.ChatCompletionMessageParam> messages = List.of();

    /**
     * 可选：Chat Completions 的 response_format（例如 json_object / json_schema）。
     */
    private final ChatCompletionCreateParams.ResponseFormat responseFormat;

    /**
     * 额外可选项：temperature/topP/maxTokens/retry 等。
     */
    @Builder.Default
    private final Map<String, Object> options = Map.of();
}

