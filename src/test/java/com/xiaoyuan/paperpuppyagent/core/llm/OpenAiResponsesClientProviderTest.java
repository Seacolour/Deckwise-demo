package com.xiaoyuan.paperpuppyagent.core.llm;

import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.Tool;
import com.xiaoyuan.paperpuppyagent.config.AgentModelCallProperties;
import com.xiaoyuan.paperpuppyagent.config.AgentOpenAiContextManagementProperties;
import com.xiaoyuan.paperpuppyagent.config.OpenAiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OpenAiResponsesClientProviderTest {
    private final OpenAiProperties openAiProperties =
            new OpenAiProperties("service-openai-key", "https://api.openai.com/v1", "gpt-test", "", "");
    private final ModelEndpointRegistry registry = new ModelEndpointRegistry(openAiProperties, "service-deepseek-key");
    private final AgentOpenAiContextManagementProperties contextManagement = enabledContextManagement();
    private final OpenAiResponsesClient client = new OpenAiResponsesClient(
            openAiProperties, new AgentModelCallProperties(), contextManagement,
            mock(AgentModelCapacityLimiter.class), registry);

    @Test
    void omitsOpenAiOnlyOptionsForDeepSeekEvenWhenRequested() {
        ModelRequestContext context = registry.resolve("deepseek-flash", "deepseek", "user-secret");
        ResponseCreateParams params = client.buildCreateParams(context, List.of(), List.of(), "hello", true, 512);
        assertTrue(params.serviceTier().isEmpty());
        assertTrue(params.contextManagement().isEmpty());
        assertTrue(params.store().isEmpty());
        assertFalse(params.tools().isPresent());
    }

    @Test
    void sendsFunctionToolsToDeepSeekResponsesWithoutOpenAiOnlyOptions() {
        ModelRequestContext context = registry.resolve("deepseek-flash", "deepseek", "user-secret");
        Tool function = Tool.ofFunction(FunctionTool.builder()
                .name("file_list")
                .description("List files")
                .parameters(FunctionTool.Parameters.builder().build())
                .strict(false)
                .build());
        ResponseCreateParams params = client.buildCreateParams(
                context, List.of(), List.of(function), "inspect files", true, 512);
        assertTrue(params.tools().isPresent());
        assertTrue(params.serviceTier().isEmpty());
        assertTrue(params.contextManagement().isEmpty());
        assertTrue(params.store().isEmpty());
    }

    @Test
    void sendsOpenRouterModelAndFunctionToolsWithoutOpenAiOnlyOptions() {
        ModelRequestContext context = registry.resolve("openai/gpt-6.1-terra", "openrouter", "user-router-key");
        Tool function = Tool.ofFunction(FunctionTool.builder()
                .name("document_read")
                .description("Read a document")
                .parameters(FunctionTool.Parameters.builder().build())
                .strict(false)
                .build());
        ResponseCreateParams params = client.buildCreateParams(
                context, List.of(), List.of(function), "read the document", true, 512);
        assertEquals("openai/gpt-6.1-terra", params.model().orElseThrow().asString());
        assertTrue(params.tools().isPresent());
        assertTrue(params.serviceTier().isEmpty());
        assertTrue(params.contextManagement().isEmpty());
        assertTrue(params.store().isEmpty());
    }

    @Test
    void preservesOpenAiPriorityAndContextManagement() {
        ModelRequestContext context = registry.resolve("gpt-test", "", "");
        ResponseCreateParams params = client.buildCreateParams(context, List.of(), List.of(), "hello", true, 512);
        assertTrue(params.serviceTier().isPresent());
        assertTrue(params.contextManagement().isPresent());
        assertTrue(params.store().isPresent());
    }

    @Test
    void providerErrorsNeverEchoPotentialCredentialText() {
        RuntimeException safe = OpenAiResponsesClient.sanitizedFailure(
                "deepseek", new IllegalStateException("invalid credential user-secret"));
        assertFalse(safe.getMessage().contains("user-secret"));
        assertNull(safe.getCause());
    }

    private static AgentOpenAiContextManagementProperties enabledContextManagement() {
        AgentOpenAiContextManagementProperties properties = new AgentOpenAiContextManagementProperties();
        properties.setEnabled(true);
        return properties;
    }
}
