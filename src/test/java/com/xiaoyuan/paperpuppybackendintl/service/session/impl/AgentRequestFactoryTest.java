package com.xiaoyuan.paperpuppybackendintl.service.session.impl;

import com.xiaoyuan.paperpuppybackendintl.grpc.client.model.AiChatRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.UserMessageSendRequest;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Session;
import com.xiaoyuan.paperpuppybackendintl.model.entity.User;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserCustomSkill;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserMemory;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserRule;
import com.xiaoyuan.paperpuppybackendintl.repository.UserCustomSkillRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserMemoryRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserRuleRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserSkillSettingRepository;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelResolutionService;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelCatalogService;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelCredentialService;
import com.xiaoyuan.paperpuppybackendintl.service.session.model.ChatExecutionPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRequestFactoryTest {

    @Test
    void resolveModelRouteUsesUserCredentialWithoutLoggingIt() {
        ModelResolutionService modelResolutionService = mock(ModelResolutionService.class);
        ModelCatalogService catalog = mock(ModelCatalogService.class);
        ModelCredentialService credentials = mock(ModelCredentialService.class);
        when(modelResolutionService.resolveForUser(7L, "gpt-5.6-sol", null))
                .thenReturn("openai/gpt-5.6-sol");
        when(catalog.findEnabledModel("openai/gpt-5.6-sol"))
                .thenReturn(Optional.of(new ModelCatalogService.ModelDefinition(
                        "openai/gpt-5.6-sol", "openai", "gpt-5.6-sol", "openai-responses",
                        true, true, "OpenAI Smart", null, true, null, null)));
        when(credentials.resolveApiKey(7L, "openai")).thenReturn(Optional.of("private-test-key"));
        AgentRequestFactory factory = new AgentRequestFactory(
                mock(UserRepository.class),
                mock(UserRuleRepository.class),
                mock(UserMemoryRepository.class),
                mock(UserSkillSettingRepository.class),
                mock(UserCustomSkillRepository.class),
                modelResolutionService, catalog, credentials
        );
        UserMessageSendRequest request = new UserMessageSendRequest();
        request.setModel("gpt-5.6-sol");

        var route = factory.resolveModelRoute(request, 7L, 101L);
        assertEquals("openai/gpt-5.6-sol", route.catalogId());
        assertEquals("gpt-5.6-sol", route.upstreamModelId());
        assertTrue(!route.toString().contains("private-test-key"));

        verify(modelResolutionService).resolveForUser(7L, "gpt-5.6-sol", null);
    }

    @Test
    void openRouterRouteKeepsTheFullUpstreamModelIdAndItsOwnCredential() {
        ModelResolutionService resolution = mock(ModelResolutionService.class);
        ModelCatalogService catalog = mock(ModelCatalogService.class);
        ModelCredentialService credentials = mock(ModelCredentialService.class);
        when(resolution.resolveForUser(7L, "openrouter/openai/gpt-6.1-terra", null))
                .thenReturn("openrouter/openai/gpt-6.1-terra");
        when(catalog.findEnabledModel("openrouter/openai/gpt-6.1-terra"))
                .thenReturn(Optional.of(new ModelCatalogService.ModelDefinition(
                        "openrouter/openai/gpt-6.1-terra", "openrouter", "openai/gpt-6.1-terra",
                        "openai-responses", true, true, "GPT-6.1 Terra · OpenRouter", null,
                        true, null, null)));
        when(credentials.resolveApiKey(7L, "openrouter")).thenReturn(Optional.of("routed-private-key"));
        AgentRequestFactory factory = new AgentRequestFactory(
                mock(UserRepository.class), mock(UserRuleRepository.class),
                mock(UserMemoryRepository.class), mock(UserSkillSettingRepository.class),
                mock(UserCustomSkillRepository.class), resolution, catalog, credentials);
        UserMessageSendRequest request = new UserMessageSendRequest();
        request.setModel("openrouter/openai/gpt-6.1-terra");

        var route = factory.resolveModelRoute(request, 7L, 101L);
        assertEquals("openrouter", route.providerId());
        assertEquals("openai/gpt-6.1-terra", route.upstreamModelId());
        assertTrue(!route.toString().contains("routed-private-key"));
        verify(credentials).resolveApiKey(7L, "openrouter");
    }

    @Test
    void createUsesValidatedSessionIdentityAndAggregatesUserPreferences() {
        UserRepository userRepository = mock(UserRepository.class);
        UserRuleRepository userRuleRepository = mock(UserRuleRepository.class);
        UserMemoryRepository userMemoryRepository = mock(UserMemoryRepository.class);
        UserSkillSettingRepository skillSettingRepository = mock(UserSkillSettingRepository.class);
        UserCustomSkillRepository customSkillRepository = mock(UserCustomSkillRepository.class);

        User user = new User();
        user.setPreferredLocale("zh-CN");
        UserRule firstRule = new UserRule();
        firstRule.setContent("Keep answers concise");
        UserRule secondRule = new UserRule();
        secondRule.setContent("Verify revisions before writes");
        UserMemory memory = new UserMemory();
        memory.setTitle("Product");
        memory.setContent("PaperPuppy");
        UserCustomSkill customSkill = new UserCustomSkill();
        customSkill.setSkillName("deck-review");
        customSkill.setContent("Review visual hierarchy");

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRuleRepository.listEnabledByUserId(7L)).thenReturn(List.of(firstRule, secondRule));
        when(userMemoryRepository.listByUserId(7L)).thenReturn(List.of(memory));
        when(skillSettingRepository.listDisabledSkillNames(7L)).thenReturn(List.of("web-search"));
        when(customSkillRepository.listEnabledByUserId(7L)).thenReturn(List.of(customSkill));

        AgentRequestFactory factory = new AgentRequestFactory(
                userRepository,
                userRuleRepository,
                userMemoryRepository,
                skillSettingRepository,
                customSkillRepository,
                mock(ModelResolutionService.class),
                mock(ModelCatalogService.class),
                mock(ModelCredentialService.class));
        Session session = new Session();
        session.setId(101L);
        session.setProjectId(501L);
        ChatExecutionPolicy policy = ChatExecutionPolicy.builder().build();

        AiChatRequest request = factory.create(new AgentRequestFactory.RequestInput(
                7L,
                session,
                "{\"version\":\"v1\"}",
                "update this slide",
                policy,
                List.of("https://cdn.example/image.png"),
                List.of("https://cdn.example/file.pdf"),
                new AgentRequestFactory.ModelRoute("openai/gpt-5.6-terra", "openai", "gpt-5.6-terra", "private-test-key")));

        assertEquals("7", request.getUserId());
        assertEquals("101", request.getSessionId());
        assertEquals(501L, request.getProjectId());
        assertEquals(policy, request.getExecutionPolicy());
        assertEquals(false, request.isVip());
        assertTrue(request.isEnableImage());
        assertEquals("zh-CN", request.getLocale());
        assertEquals("- Keep answers concise\n- Verify revisions before writes", request.getCustomRules());
        assertEquals("- Product: PaperPuppy", request.getUserMemories());
        assertEquals(List.of("web-search"), request.getDisabledSkillNames());
        assertTrue(request.getUserSkillPrompt().contains("### deck-review"));
        assertEquals("gpt-5.6-terra", request.getModel());
        assertEquals("openai", request.getProviderId());
        assertTrue(!request.toString().contains("private-test-key"));
    }

    @Test
    void createFallsBackWhenOptionalPreferenceSourcesFail() {
        UserRepository userRepository = mock(UserRepository.class);
        UserRuleRepository userRuleRepository = mock(UserRuleRepository.class);
        UserMemoryRepository userMemoryRepository = mock(UserMemoryRepository.class);
        UserSkillSettingRepository skillSettingRepository = mock(UserSkillSettingRepository.class);
        UserCustomSkillRepository customSkillRepository = mock(UserCustomSkillRepository.class);

        when(userRepository.findById(7L)).thenThrow(new IllegalStateException("user unavailable"));
        when(userRuleRepository.listEnabledByUserId(7L)).thenThrow(new IllegalStateException("rules unavailable"));
        when(userMemoryRepository.listByUserId(7L)).thenThrow(new IllegalStateException("memory unavailable"));
        when(skillSettingRepository.listDisabledSkillNames(7L)).thenThrow(new IllegalStateException("skills unavailable"));
        when(customSkillRepository.listEnabledByUserId(7L)).thenThrow(new IllegalStateException("custom unavailable"));

        AgentRequestFactory factory = new AgentRequestFactory(
                userRepository,
                userRuleRepository,
                userMemoryRepository,
                skillSettingRepository,
                customSkillRepository,
                mock(ModelResolutionService.class),
                mock(ModelCatalogService.class),
                mock(ModelCredentialService.class));
        Session session = new Session();
        session.setId(101L);
        session.setProjectId(501L);

        AiChatRequest request = factory.create(new AgentRequestFactory.RequestInput(
                7L, session, "{}", "hello", null, List.of(), List.of(),
                new AgentRequestFactory.ModelRoute("openai/gpt-5.6-terra", "openai", "gpt-5.6-terra", "private-test-key")));

        assertEquals("7", request.getUserId());
        assertEquals(501L, request.getProjectId());
        assertEquals(false, request.isVip());
        assertEquals(false, request.isEnableImage());
        assertEquals(List.of(), request.getDisabledSkillNames());
        assertEquals(null, request.getLocale());
        assertEquals(null, request.getCustomRules());
        assertEquals(null, request.getUserMemories());
        assertEquals(null, request.getUserSkillPrompt());
    }
}
