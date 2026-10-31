package com.xiaoyuan.paperpuppybackendintl.grpc.client;

import com.xiaoyuan.paperpuppybackendintl.grpc.client.model.AiChatRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.client.model.AiChatMetadata;
import com.xiaoyuan.paperpuppybackendintl.grpc.client.model.ChatCiFileRef;
import com.xiaoyuan.paperpuppybackendintl.grpc.config.AgentGrpcProperties;
import com.xiaoyuan.paperpuppybackendintl.grpc.mapper.AgentGrpcMapper;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.AgentChatResponse;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Agent gRPC 调用封装（对外主入口）。
 *
 * <p>职责：统一日志与异常边界；业务层（SSE/落库）仍由 ChatService 处理。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentGrpcClient {

    private final com.xiaoyuan.paperpuppybackendintl.grpc.agent.AgentServiceGrpc.AgentServiceStub stub;
    private final AgentGrpcProperties properties;

    public ChatGrpcCall chatStream(AiChatRequest request, ChatStreamObserver observer) {
        final java.util.concurrent.atomic.AtomicReference<ClientCallStreamObserver<?>> callRef = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);

        ChatGrpcCall handle = reason -> {
            cancelled.set(true);
            ClientCallStreamObserver<?> call = callRef.get();
            if (call != null) {
                call.cancel(reason, null);
            }
        };

        com.xiaoyuan.paperpuppybackendintl.grpc.agent.AgentChatRequest grpcReq;
        try {
            grpcReq = AgentGrpcMapper.toGrpc(request);
        } catch (IllegalArgumentException e) {
            log.warn("Agent gRPC 请求参数校验失败: {}", e.getMessage());
            observer.onError(e);
            return handle;
        }
        try {
            long deadlineMs = properties != null ? properties.getDeadlineMs() : 0L;
            if (deadlineMs <= 0) {
                deadlineMs = 270_000L; // 安全兜底：270s（>= Agent max-total-cap 240s + 余量）
            }
            String target = (properties != null ? properties.getHost() : "?") + ":" + (properties != null ? properties.getPort() : -1);
            boolean plaintext = properties != null && properties.isPlaintext();
            com.xiaoyuan.paperpuppybackendintl.grpc.agent.AgentServiceGrpc.AgentServiceStub callStub = stub
                    .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS);
            log.info("Agent gRPC chat start: target={}, plaintext={}, deadlineMs={}, service={}/{}",
                    target, plaintext, deadlineMs, "paperpuppy.backendintl.agent.AgentService", "Chat");

            callStub.chat(grpcReq, new ClientResponseObserver<com.xiaoyuan.paperpuppybackendintl.grpc.agent.AgentChatRequest, AgentChatResponse>() {
                @Override
                public void beforeStart(ClientCallStreamObserver<com.xiaoyuan.paperpuppybackendintl.grpc.agent.AgentChatRequest> requestStream) {
                    callRef.set(requestStream);
                    if (cancelled.get()) {
                        requestStream.cancel("cancelled before start", null);
                    }
                }

                @Override
                public void onNext(AgentChatResponse value) {
                    if (cancelled.get()) {
                        return;
                    }
                    try {
                        if (value.hasText()) {
                            String chunk = value.getText().getText();
                            if (!chunk.isEmpty()) {
                                observer.onText(chunk);
                            }
                        } else if (value.hasMetadata()) {
                            AiChatMetadata meta = new AiChatMetadata(
                                    value.getMetadata().getKeyword(),
                                    value.getMetadata().getInfoMap(),
                                    value.getMetadata().getLinksList().stream()
                                            .map(l -> new com.xiaoyuan.paperpuppybackendintl.grpc.client.model.AiChatLink(
                                                    l.getTitle(),
                                                    l.getUrl(),
                                                    l.getDescription(),
                                                    l.getResources(),
                                                    l.getPublicationInfo()
                                            ))
                                            .toList()
                            );
                            observer.onMetadata(meta);
                        } else if (value.hasToolCall()) {
                            observer.onToolCall(
                                    value.getToolCall().getCallId(),
                                    value.getToolCall().getName(),
                                    value.getToolCall().getArgumentsJson()
                            );
                        } else if (value.hasToolResult()) {
                            observer.onToolResult(
                                    value.getToolResult().getCallId(),
                                    value.getToolResult().getName(),
                                    value.getToolResult().getResultJson()
                            );
                        } else if (value.hasFinalResponse()) {
                            var agentFinal = value.getFinalResponse();
                            java.util.List<ChatCiFileRef> ciFiles =
                                    agentFinal.getCiFilesList().stream()
                                            .map(src -> new ChatCiFileRef(
                                                    src.getContainerId(),
                                                    src.getFileId(),
                                                    src.getFileName()))
                                            .toList();
                            observer.onResponse(agentFinal.getResponseMetaMap(), ciFiles);
                        } else if (value.hasDone()) {
                            // Agent 发送 Done 表示逻辑完成；gRPC 流随后会触发 onCompleted()，
                            // 由 onCompleted() 统一调用 observer.onCompleted()，避免双重触发。
                        } else if (value.hasError()) {
                            observer.onError(new RuntimeException(
                                    "agent error: " + value.getError().getCode() + " " + value.getError().getMessage()
                            ));
                        }
                    } catch (Exception e) {
                        observer.onError(e);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    observer.onError(t);
                }

                @Override
                public void onCompleted() {
                    observer.onCompleted();
                }
            });
        } catch (StatusRuntimeException e) {
            String target = (properties != null ? properties.getHost() : "?") + ":" + (properties != null ? properties.getPort() : -1);
            log.error("Agent gRPC 调用失败 target={}, status={}, desc={}",
                    target, e.getStatus().getCode(), e.getStatus().getDescription(), e);
            observer.onError(e);
        }

        return handle;
    }
}
