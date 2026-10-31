package com.xiaoyuan.paperpuppyagent.core;

import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatResponse;
import com.xiaoyuan.paperpuppyagent.grpc.agent.ToolCall;
import com.xiaoyuan.paperpuppyagent.grpc.agent.ToolResult;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

/** Publishes tool call/result events to the agent gRPC stream in protocol order. */
@Component
public class ToolEventPublisher {
    private static final int TOOL_PAYLOAD_MAX_CHARS = 20_000;

    public void publishCall(StreamObserver<AgentChatResponse> out,
                            ToolInvocationExecutor.InvocationResult invocation) {
        out.onNext(AgentChatResponse.newBuilder()
                .setToolCall(ToolCall.newBuilder()
                        .setCallId(invocation.callId())
                        .setName(invocation.name())
                        .setArgumentsJson(truncate(invocation.argumentsJson(), TOOL_PAYLOAD_MAX_CHARS))
                        .build())
                .build());
    }

    public void publishResult(StreamObserver<AgentChatResponse> out,
                              ToolInvocationExecutor.InvocationResult invocation,
                              ToolResultProcessor.ProcessedToolResult processed) {
        out.onNext(AgentChatResponse.newBuilder()
                .setToolResult(ToolResult.newBuilder()
                        .setCallId(invocation.callId())
                        .setName(invocation.name())
                        .setResultJson(truncate(processed.resultJson(), TOOL_PAYLOAD_MAX_CHARS))
                        .build())
                .build());
    }

    private static String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() <= maxChars
                ? text
                : text.substring(0, maxChars) + "...(truncated,len=" + text.length() + ")";
    }
}
