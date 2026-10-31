package com.xiaoyuan.paperpuppyagent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.Tool;
import com.openai.models.responses.WebSearchTool;
import com.xiaoyuan.paperpuppyagent.config.AgentWorkMemoryProperties;
import com.xiaoyuan.paperpuppyagent.config.OpenAiProperties;
import com.xiaoyuan.paperpuppyagent.core.composition.ToolCompositionRegistry;
import com.xiaoyuan.paperpuppyagent.core.llm.ModelEndpointRegistry;
import com.xiaoyuan.paperpuppyagent.core.prompt.AgentInstructionsProvider;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillCatalog;
import com.xiaoyuan.paperpuppyagent.core.skill.binding.SkillBindingCatalog;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentInputAssemblerTest {
    private final ObjectMapper om = new ObjectMapper();

    @Test
    void askOnlyRequestDoesNotExposeToolsAndUsesConfiguredModelFallback() {
        SkillCatalog skillCatalog = mock(SkillCatalog.class);
        AgentInputAssembler assembler = newAssembler(
                new OpenAiProperties("test-key", "", "gpt-config", "", ""),
                skillCatalog,
                mock(SkillBindingCatalog.class),
                instructionsProvider("base instructions"),
                workMemoryProperties());

        AgentChatRequest request = AgentChatRequest.newBuilder()
                .setUserId(11)
                .setProjectId(22)
                .setSessionId(33)
                .setMessage("hello")
                .setEnableTools(false)
                .build();

        AgentInputAssembler.PreparedInput prepared = assembler.assemble(request, "run-ask");

        assertEquals("11:33", prepared.sessionKey());
        assertEquals("gpt-config", prepared.model());
        assertTrue(prepared.tools().isEmpty());
        assertTrue(prepared.instructions().contains("No tools are available"));
        assertEquals(1, prepared.loopHistoryStartIndex());
        assertEquals(1, prepared.input().size());
        assertEasyUserMessage(prepared.input().getFirst(), "hello");
        assertEquals(11, prepared.toolContext().userId());
        assertEquals(22, prepared.toolContext().projectId());
        assertEquals(33, prepared.toolContext().sessionId());
        verify(skillCatalog, never()).toOpenAiSdkTools();
    }

    @Test
    void blankRequestedAndConfiguredModelFallsBackToDefaultModel() {
        AgentInputAssembler assembler = newAssembler(
                new OpenAiProperties("test-key", "", "", "", ""),
                mock(SkillCatalog.class),
                mock(SkillBindingCatalog.class),
                instructionsProvider("base instructions"),
                workMemoryProperties());

        AgentChatRequest request = AgentChatRequest.newBuilder()
                .setMessage("hello")
                .setEnableTools(false)
                .build();

        AgentInputAssembler.PreparedInput prepared = assembler.assemble(request, "run-default-model");

        assertEquals("gpt-4.1-mini", prepared.model());
    }

    @Test
    void instructionsIncludeCompositionUserMemoriesAndCustomRules() {
        ToolCompositionRegistry compositionRegistry = mock(ToolCompositionRegistry.class);
        when(compositionRegistry.instructionsForModel()).thenReturn("composition guide");
        AgentInstructionsProvider instructionsProvider = instructionsProvider("base instructions");
        SkillCatalog skillCatalog = mock(SkillCatalog.class);
        when(skillCatalog.toOpenAiSdkTools(any(), any())).thenReturn(List.of());
        AgentInputAssembler assembler = newAssembler(
                new OpenAiProperties("test-key", "", "gpt-config", "", ""),
                skillCatalog,
                mock(SkillBindingCatalog.class),
                instructionsProvider,
                workMemoryProperties());
        assembler.setToolCompositionRegistry(compositionRegistry);

        AgentChatRequest request = AgentChatRequest.newBuilder()
                .setLocale("zh-CN")
                .setMessage("hello")
                .setEnableTools(true)
                .setUserMemories("likes concise answers")
                .setCustomRules("never overwrite user changes")
                .build();

        AgentInputAssembler.PreparedInput prepared = assembler.assemble(request, "run-instructions");

        assertEquals("""
                base instructions

                composition guide

                ## User Memories
                likes concise answers

                ## User Custom Rules
                never overwrite user changes""", prepared.instructions());
        verify(instructionsProvider).get("zh-CN");
    }

    @Test
    void explicitDeepSeekByokSupportsToolsButStillRejectsFileAttachments() {
        SkillCatalog skillCatalog = mock(SkillCatalog.class);
        when(skillCatalog.toOpenAiSdkTools(any(), any())).thenReturn(List.of());
        AgentInputAssembler assembler = newAssembler(
                new OpenAiProperties("service-openai-key", "", "gpt-config", "", ""),
                skillCatalog, mock(SkillBindingCatalog.class),
                instructionsProvider("base instructions"), workMemoryProperties());
        AgentChatRequest textRequest = AgentChatRequest.newBuilder()
                .setMessage("hello")
                .setModel("deepseek-flash")
                .setProviderId("deepseek")
                .setProviderApiKey("user-secret")
                .build();
        AgentInputAssembler.PreparedInput prepared = assembler.assemble(textRequest, "run-deepseek");
        assertEquals("deepseek", prepared.modelContext().providerId());
        assertEquals("deepseek-flash", prepared.model());
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(
                textRequest.toBuilder().addFileUrls("https://example.com/test.pdf").build(), "run-file"));
        assertTrue(assembler.assemble(textRequest.toBuilder().setEnableTools(true).build(), "run-tools")
                .modelContext().endpoint().supportsTools());
    }

    @Test
    void openRouterKeepsFunctionToolsAndOmitsUnconfiguredServerWebSearch() {
        SkillCatalog skillCatalog = mock(SkillCatalog.class);
        Tool function = Tool.ofFunction(FunctionTool.builder()
                .name("document_read").parameters(FunctionTool.Parameters.builder().build())
                .strict(false).build());
        Tool webSearch = Tool.ofWebSearch(WebSearchTool.builder()
                .type(WebSearchTool.Type.WEB_SEARCH).build());
        when(skillCatalog.toOpenAiSdkTools(any(), any())).thenReturn(List.of(function, webSearch));
        AgentInputAssembler assembler = newAssembler(
                new OpenAiProperties("service-openai-key", "", "gpt-config", "", ""),
                skillCatalog, mock(SkillBindingCatalog.class),
                instructionsProvider("base instructions"), workMemoryProperties());
        AgentChatRequest request = AgentChatRequest.newBuilder()
                .setMessage("edit this document")
                .setModel("openai/gpt-6-luna")
                .setProviderId("openrouter")
                .setProviderApiKey("user-router-key")
                .setEnableTools(true)
                .build();

        AgentInputAssembler.PreparedInput prepared = assembler.assemble(request, "run-openrouter");
        assertEquals("openrouter", prepared.modelContext().providerId());
        assertEquals("openai/gpt-6-luna", prepared.model());
        assertEquals(1, prepared.tools().size());
        assertTrue(prepared.tools().getFirst().isFunction());
    }

    private AgentInputAssembler newAssembler(OpenAiProperties openAiProperties,
                                             SkillCatalog skillCatalog,
                                             SkillBindingCatalog bindingCatalog,
                                             AgentInstructionsProvider instructionsProvider,
                                             AgentWorkMemoryProperties workMemoryProperties) {
        return new AgentInputAssembler(
                openAiProperties,
                skillCatalog,
                bindingCatalog,
                new AgentConversationMapper(om),
                instructionsProvider,
                workMemoryProperties,
                new ModelEndpointRegistry(openAiProperties, ""));
    }

    private AgentInstructionsProvider instructionsProvider(String instructions) {
        AgentInstructionsProvider provider = mock(AgentInstructionsProvider.class);
        when(provider.get(org.mockito.ArgumentMatchers.any())).thenReturn(instructions);
        return provider;
    }

    private AgentWorkMemoryProperties workMemoryProperties() {
        AgentWorkMemoryProperties properties = new AgentWorkMemoryProperties();
        properties.setEnabled(true);
        return properties;
    }

    private static void assertEasyUserMessage(ResponseInputItem item, String text) {
        assertTrue(item.isEasyInputMessage());
        assertEquals(EasyInputMessage.Role.USER, item.asEasyInputMessage().role());
        assertTrue(item.asEasyInputMessage().content().toString().contains(text));
    }
}
