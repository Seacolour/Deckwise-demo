package com.xiaoyuan.paperpuppyagent.core.llm;

import com.xiaoyuan.paperpuppyagent.config.OpenAiProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelEndpointRegistryTest {
    private final ModelEndpointRegistry registry = new ModelEndpointRegistry(
            new OpenAiProperties("service-openai-key", "https://api.openai.com/v1", "gpt-test", "", ""),
            "service-deepseek-key");

    @Test
    void implicitLegacyOpenAiRouteKeepsConfiguredEndpoint() {
        ModelRequestContext context = registry.resolve("gpt-test", "", "");
        assertEquals("openai", context.providerId());
        assertEquals("gpt-test", context.upstreamModel());
        assertEquals("https://api.openai.com/v1", context.endpoint().baseUrl());
        assertTrue(context.legacyOpenAiConfiguration());
        assertFalse(context.toString().contains("service-openai-key"));
    }

    @Test
    void implicitDeepSeekRouteUsesOnlyAllowlistedEndpoint() {
        ModelRequestContext context = registry.resolve("deepseek:deepseek-flash", "", "");
        assertEquals("deepseek", context.providerId());
        assertEquals("deepseek-flash", context.upstreamModel());
        assertEquals("https://api.deepseek.com", context.endpoint().baseUrl());
        assertFalse(context.endpoint().supportsFiles());
        assertFalse(context.legacyOpenAiConfiguration());
    }

    @Test
    void explicitByokProviderRequiresItsOwnKeyAndNeverFallsBack() {
        assertThrows(IllegalStateException.class,
                () -> registry.resolve("deepseek-flash", "deepseek", ""));
        assertThrows(IllegalStateException.class,
                () -> registry.resolve("gpt-test", "openai", ""));
        assertThrows(IllegalStateException.class,
                () -> registry.resolve("openai/gpt-6.1-terra", "openrouter", ""));

        ModelRequestContext context = registry.resolve("deepseek-v4-pro", "deepseek", "user-secret");
        assertEquals("user-secret", context.apiKey());
        assertFalse(context.toString().contains("user-secret"));
        assertFalse(context.legacyOpenAiConfiguration());
    }

    @Test
    void openRouterUsesItsFixedResponsesEndpointAndPreservesTheQualifiedModel() {
        ModelRequestContext context = registry.resolve("openai/gpt-6.1-terra", "openrouter", "user-router-key");
        assertEquals("openrouter", context.providerId());
        assertEquals("openai/gpt-6.1-terra", context.upstreamModel());
        assertEquals("https://openrouter.ai/api/v1", context.endpoint().baseUrl());
        assertTrue(context.endpoint().supportsTools());
        assertTrue(context.endpoint().supportsImages());
        assertFalse(context.endpoint().supportsFiles());
        assertFalse(context.endpoint().supportsContextManagement());
        assertFalse(context.legacyOpenAiConfiguration());
        assertFalse(context.toString().contains("user-router-key"));
        registry.validateRequest(context, true, 1, 0);
        assertThrows(IllegalArgumentException.class, () -> registry.validateRequest(context, true, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("gpt-6.1-terra", "openrouter", "user-router-key"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("openrouter:openai/gpt-6.1-terra", "", ""));
    }

    @Test
    void rejectsProviderMismatchAndUnsupportedDeepSeekAttachments() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("deepseek-flash", "openai", "user-secret"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("deepseek-unknown", "deepseek", "user-secret"));
        ModelRequestContext context = registry.resolve("deepseek-flash", "deepseek", "user-secret");
        registry.validateRequest(context, true, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> registry.validateRequest(context, false, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> registry.validateRequest(context, false, 0, 1));
        registry.validateRequest(context, false, 0, 0);
    }
}
