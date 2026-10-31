package com.xiaoyuan.paperpuppybackendintl.grpc.mapper;

import com.xiaoyuan.paperpuppybackendintl.grpc.agent.AgentChatRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.client.model.AiChatRequest;
import com.xiaoyuan.paperpuppybackendintl.model.enums.ConversationMode;
import com.xiaoyuan.paperpuppybackendintl.service.session.model.ChatExecutionPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionPolicyGrpcMapperTest {

    @Test
    void agentGrpcMapperOnlyUsesAgentSkillsFlag() {
        AiChatRequest request = baseRequest(policy(true));

        AgentChatRequest grpcRequest = AgentGrpcMapper.toGrpc(request);

        assertTrue(grpcRequest.getEnableTools());
    }

    @Test
    void agentGrpcMapperKeepsToolsOffWhenAgentSkillsAreDisabled() {
        AiChatRequest request = baseRequest(policy(false));

        AgentChatRequest grpcRequest = AgentGrpcMapper.toGrpc(request);

        assertFalse(grpcRequest.getEnableTools());
    }

    @Test
    void agentGrpcMapperPropagatesPriorityProcessingFlag() {
        AiChatRequest request = baseRequest(policy(true, true));

        AgentChatRequest grpcRequest = AgentGrpcMapper.toGrpc(request);

        assertTrue(grpcRequest.getEnablePriorityProcessing());
    }

    private static AiChatRequest baseRequest(ChatExecutionPolicy policy) {
        return AiChatRequest.builder()
                .userId("1")
                .sessionId("2")
                .fullContextJson("{\"version\":\"v1\",\"items\":[]}")
                .message("hello")
                .executionPolicy(policy)
                .build();
    }

    private static ChatExecutionPolicy policy(boolean allowAgentSkills) {
        return policy(allowAgentSkills, false);
    }

    private static ChatExecutionPolicy policy(boolean allowAgentSkills,
                                              boolean useOpenAiPriorityProcessing) {
        return ChatExecutionPolicy.builder()
                .requestedMode(ConversationMode.ASK)
                .resolvedRoute(ChatExecutionPolicy.ResolvedRoute.AGENT)
                .allowAgentSkills(allowAgentSkills)
                .useOpenAiPriorityProcessing(useOpenAiPriorityProcessing)
                .build();
    }
}
