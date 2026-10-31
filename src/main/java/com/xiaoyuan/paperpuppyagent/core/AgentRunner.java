package com.xiaoyuan.paperpuppyagent.core;

import com.xiaoyuan.paperpuppyagent.core.composition.ToolCompositionRegistry;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatRequest;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatResponse;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** Stable gRPC-facing facade for the agent run loop. */
@Component
@RequiredArgsConstructor
public class AgentRunner {
    private final AgentLoopCoordinator loopCoordinator;

    public void run(AgentChatRequest request,
                    StreamObserver<AgentChatResponse> out,
                    AtomicBoolean cancelled,
                    Context ctx) {
        loopCoordinator.run(request, out, cancelled, ctx);
    }

    public void setToolCompositionRegistry(ToolCompositionRegistry toolCompositionRegistry) {
        loopCoordinator.setToolCompositionRegistry(toolCompositionRegistry);
    }
}
