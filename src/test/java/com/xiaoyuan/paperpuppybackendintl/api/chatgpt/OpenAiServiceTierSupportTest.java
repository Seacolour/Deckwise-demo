package com.xiaoyuan.paperpuppybackendintl.api.chatgpt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpenAiServiceTierSupportTest {

    @Test
    void resolveUsesParamServiceTierByDefault() {
        ApplicationParam param = ApplicationParam.builder()
                .apiKey("test-key")
                .model("gpt-5.2")
                .serviceTier("priority")
                .build();

        assertEquals("priority", OpenAiServiceTierSupport.resolve(param));
    }

    @Test
    void resolveLetsPerCallOptionOverrideGlobalDefault() {
        ApplicationParam param = ApplicationParam.builder()
                .apiKey("test-key")
                .model("gpt-5.2")
                .serviceTier("default")
                .options(Map.of("model.serviceTier", "fast"))
                .build();

        assertEquals("priority", OpenAiServiceTierSupport.resolve(param));
    }

    @Test
    void normalizeSupportsLegacyAliasesForGenerationParamsToo() {
        GenerationParam param = GenerationParam.builder()
                .apiKey("test-key")
                .model("gpt-5.2")
                .serviceTier("standard")
                .build();

        assertEquals("default", OpenAiServiceTierSupport.resolve(param));
    }

    @Test
    void resolveReturnsNullWhenUnset() {
        ApplicationParam param = ApplicationParam.builder()
                .apiKey("test-key")
                .model("gpt-5.2")
                .build();

        assertNull(OpenAiServiceTierSupport.resolve(param));
    }
}
