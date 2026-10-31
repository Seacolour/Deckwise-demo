package com.xiaoyuan.paperpuppyagent.grpc.server.service;

import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatRequest;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatResponse;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentServiceGrpc;
import com.xiaoyuan.paperpuppyagent.core.AgentRunner;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 对外 gRPC 服务。
 *
 * <p>
 * 当前版本：Agent 是主执行入口。
 * enableTools=false 或 agent.mode=ask_only 时走 no-tools / pure chat；
 * enableTools=true 时走正常 Agent tool loop。
 * </p>
 */
@Slf4j
@Service
public class AgentServiceGrpcImpl extends AgentServiceGrpc.AgentServiceImplBase {

    private final AgentRunner agentRunner;
    private final String mode;

    public AgentServiceGrpcImpl(
            AgentRunner agentRunner,
            @Value("${agent.mode:agent_rag}") String mode) {
        this.agentRunner = agentRunner;
        this.mode = mode;
    }

    @Override
    public void chat(AgentChatRequest request, StreamObserver<AgentChatResponse> responseObserver) {
        final ServerCallStreamObserver<AgentChatResponse> serverObserver = (responseObserver instanceof ServerCallStreamObserver)
                ? (ServerCallStreamObserver<AgentChatResponse>) responseObserver
                : null;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        if (serverObserver != null) {
            serverObserver.setOnCancelHandler(() -> cancelled.set(true));
        }

        // 路由：
        // - ask_only -> 本地 AgentRunner，但强制关闭 tools，作为 ASK / pure chat 分支。
        // - agent_rag/agent_only/agent_prefer -> 本地 AgentRunner，根据请求 enableTools 决定是否允许 tools。
        String m = Objects.toString(mode, "").trim();
        boolean forceAskOnly = "ask_only".equalsIgnoreCase(m) || "ask-only".equalsIgnoreCase(m);
        AgentChatRequest effectiveRequest = forceAskOnly
                ? request.toBuilder()
                        .setEnableTools(false)
                        .build()
                : request;
        boolean shouldAgent = forceAskOnly || "agent_rag".equalsIgnoreCase(m)
                || "agent_only".equalsIgnoreCase(m)
                || "agent_prefer".equalsIgnoreCase(m);

        if (shouldAgent) {
            String runnerMode = effectiveRequest.getEnableTools() ? "agent_loop" : "ask_only";
            log.info("Agent运行时请求: userId={}, sessionId={}, mode={}, runnerMode={}, enableTools={}",
                    effectiveRequest.getUserId(),
                    effectiveRequest.getSessionId(),
                    m,
                    runnerMode,
                    effectiveRequest.getEnableTools());
            agentRunner.run(effectiveRequest, responseObserver, cancelled, Context.current());
            return;
        }

        log.error("不支持的Agent模式: mode={}", mode);
        responseObserver.onError(Status.UNIMPLEMENTED
                .withDescription("agent mode not implemented yet, please use agent_rag/agent_only/agent_prefer/ask_only")
                .asRuntimeException());
    }
}
