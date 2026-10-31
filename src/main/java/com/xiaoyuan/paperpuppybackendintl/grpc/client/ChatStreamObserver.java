package com.xiaoyuan.paperpuppybackendintl.grpc.client;

import com.xiaoyuan.paperpuppybackendintl.grpc.client.model.AiChatMetadata;
import com.xiaoyuan.paperpuppybackendintl.grpc.client.model.ChatCiFileRef;

import java.util.List;
import java.util.Map;

/**
 * 对话流式回调（业务侧接口）。
 */
public interface ChatStreamObserver {
    void onText(String chunk);

    void onMetadata(AiChatMetadata metadata);

    void onResponse(Map<String, String> responseMeta, List<ChatCiFileRef> ciFiles);

    /**
     * Agent 模式下的 tool_call 事件。
     */
    default void onToolCall(String callId, String name, String argumentsJson) {
    }

    /**
     * Agent 模式下的 tool_result 事件。
     */
    default void onToolResult(String callId, String name, String resultJson) {
    }

    void onError(Throwable t);

    void onCompleted();
}
