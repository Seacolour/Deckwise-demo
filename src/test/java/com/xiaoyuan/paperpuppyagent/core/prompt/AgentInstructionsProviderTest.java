package com.xiaoyuan.paperpuppyagent.core.prompt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgentInstructionsProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void mapsLegacyCnLocaleToStandardZhPrompt() throws Exception {
        Files.writeString(tempDir.resolve("agent_loop_instructions_zh.md"), "standard zh instructions");

        AgentInstructionsProvider provider = provider();

        assertThat(provider.get("cn")).isEqualTo("standard zh instructions");
    }

    @Test
    void keepsStandardChineseLocaleOnZhPrompt() throws Exception {
        Files.writeString(tempDir.resolve("agent_loop_instructions_zh.md"), "standard zh instructions");

        AgentInstructionsProvider provider = provider();

        assertThat(provider.get("zh-CN")).isEqualTo("standard zh instructions");
        assertThat(provider.get("zh_Hans")).isEqualTo("standard zh instructions");
    }

    @Test
    void keepsTraditionalChineseLocaleOnZhHantPrompt() throws Exception {
        Files.writeString(tempDir.resolve("agent_loop_instructions_zh-hant.md"), "traditional zh instructions");

        AgentInstructionsProvider provider = provider();

        assertThat(provider.get("zh-Hant")).isEqualTo("traditional zh instructions");
    }

    private AgentInstructionsProvider provider() {
        AgentInstructionsProvider provider = new AgentInstructionsProvider(new DefaultResourceLoader());
        ReflectionTestUtils.setField(provider, "instructionsDir", tempDir.toUri().toString());
        ReflectionTestUtils.setField(provider, "instructionsPrefix", "agent_loop_instructions_");
        ReflectionTestUtils.setField(provider, "instructionsSuffix", ".md");
        ReflectionTestUtils.setField(provider, "inlineInstructions", "");
        return provider;
    }
}
