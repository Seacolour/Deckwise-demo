package com.xiaoyuan.paperpuppybackendintl.service.session.policy;

import com.xiaoyuan.paperpuppybackendintl.config.ChatAgentProperties;
import com.xiaoyuan.paperpuppybackendintl.config.ChatExecutionPolicyProperties;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.UserMessageSendRequest;
import com.xiaoyuan.paperpuppybackendintl.model.enums.ConversationMode;
import com.xiaoyuan.paperpuppybackendintl.service.session.model.ChatExecutionPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatExecutionPolicyResolverTest {

    @Test
    void defaultsToAskWhenModeIsMissing() {
        ChatExecutionPolicyResolver resolver = newResolver(true);

        ChatExecutionPolicy policy = resolver.resolve(new UserMessageSendRequest());

        assertEquals(ConversationMode.ASK, policy.getRequestedMode());
        assertEquals(ChatExecutionPolicy.ResolvedRoute.AGENT, policy.getResolvedRoute());
        assertFalse(policy.isAllowAgentSkills());
    }

    @Test
    void failsFastWhenAgentRuntimeIsDisabled() {
        ChatExecutionPolicyResolver resolver = newResolver(false);
        UserMessageSendRequest request = new UserMessageSendRequest();
        request.setMode(ConversationMode.AGENT);

        assertThrows(BusinessException.class, () -> resolver.resolve(request));
    }

    @Test
    void enablesOnlyAgentSideCapabilitiesOnAgentRoute() {
        ChatExecutionPolicyResolver resolver = newResolver(true);
        UserMessageSendRequest request = new UserMessageSendRequest();
        request.setMode(ConversationMode.AGENT);

        ChatExecutionPolicy policy = resolver.resolve(request);

        assertEquals(ChatExecutionPolicy.ResolvedRoute.AGENT, policy.getResolvedRoute());
        assertTrue(policy.isAllowAgentSkills());
    }

    @Test
    void keepsAgentToolsOffOnAskRoute() {
        ChatExecutionPolicyResolver resolver = newResolver(true);
        UserMessageSendRequest request = new UserMessageSendRequest();
        request.setMode(ConversationMode.ASK);

        ChatExecutionPolicy policy = resolver.resolve(request);

        assertEquals(ChatExecutionPolicy.ResolvedRoute.AGENT, policy.getResolvedRoute());
        assertFalse(policy.isAllowAgentSkills());
    }

    @Test
    void serviceTierPriorityEnablesPriorityProcessing() {
        ChatExecutionPolicyResolver resolver = newResolver(true);
        UserMessageSendRequest request = new UserMessageSendRequest();
        request.setServiceTier("priority");

        ChatExecutionPolicy policy = resolver.resolve(request);

        assertTrue(policy.isUseOpenAiPriorityProcessing());
    }

    @Test
    void serviceTierFastAliasEnablesPriorityProcessing() {
        ChatExecutionPolicyResolver resolver = newResolver(true);
        UserMessageSendRequest request = new UserMessageSendRequest();
        request.setServiceTier("fast");

        ChatExecutionPolicy policy = resolver.resolve(request);

        assertTrue(policy.isUseOpenAiPriorityProcessing());
    }

    @Test
    void serviceTierDefaultOverridesLegacyBoolean() {
        ChatExecutionPolicyResolver resolver = newResolver(true);
        UserMessageSendRequest request = new UserMessageSendRequest();
        request.setEnablePriorityProcessing(true);
        request.setServiceTier("default");

        ChatExecutionPolicy policy = resolver.resolve(request);

        assertFalse(policy.isUseOpenAiPriorityProcessing());
    }

    @Test
    void rejectsUnsupportedServiceTier() {
        ChatExecutionPolicyResolver resolver = newResolver(true);
        UserMessageSendRequest request = new UserMessageSendRequest();
        request.setServiceTier("batch");

        assertThrows(BusinessException.class, () -> resolver.resolve(request));
    }

    private static ChatExecutionPolicyResolver newResolver(boolean agentEnabled) {
        ChatAgentProperties agentProperties = new ChatAgentProperties();
        agentProperties.setEnabled(agentEnabled);

        ChatExecutionPolicyProperties executionPolicyProperties = new ChatExecutionPolicyProperties();
        executionPolicyProperties.setAllowAgentSkills(true);

        return new ChatExecutionPolicyResolver(agentProperties, executionPolicyProperties);
    }
}
