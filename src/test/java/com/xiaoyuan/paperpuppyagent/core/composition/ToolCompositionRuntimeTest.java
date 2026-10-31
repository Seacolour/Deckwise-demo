package com.xiaoyuan.paperpuppyagent.core.composition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillCatalog;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillExecutor;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillFunction;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillSafety;
import com.xiaoyuan.paperpuppyagent.core.skill.runtime.SkillExecutionResult;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolCompositionRuntimeTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void jsonStructureComposeRunsSubtreeReadForDeclaredChildPath() {
        SkillCatalog catalog = catalogWithReadTools("json_structure_read", "json_subtree_read");
        SkillExecutor executor = mock(SkillExecutor.class);
        ToolCompositionRuntime runtime = newRuntime(catalog, executor);
        when(executor.execute(eq("json_structure_read"), any(), any()))
                .thenReturn(SkillExecutionResult.toolResult(structureResult()));
        ObjectNode subtree = om.createObjectNode();
        subtree.put("success", true);
        subtree.put("file_id", 55);
        subtree.put("path", "/cards");
        subtree.putObject("value").put("title", "Intro");
        when(executor.execute(eq("json_subtree_read"), any(), any()))
                .thenReturn(SkillExecutionResult.toolResult(subtree));

        JsonNode result = runtime.execute("json_structure_read", composeArgs("/cards"),
                ToolExecutionContext.empty()).payload();

        assertEquals("completed", result.path("composition").path("status").asText());
        verify(executor).execute(eq("json_subtree_read"), argThat(args ->
                args.path("file_id").asLong() == 55
                        && !args.has("document_id")
                        && "/cards".equals(args.path("path").asText())
                        && args.path("max_bytes").asInt() == 2048
                        && args.path("max_nodes").asInt() == 100), any());
    }

    @Test
    void jsonStructureComposeSkipsUndeclaredPath() {
        SkillExecutor executor = mock(SkillExecutor.class);
        ToolCompositionRuntime runtime = newRuntime(catalogWithReadTools("json_structure_read", "json_subtree_read"), executor);
        when(executor.execute(eq("json_structure_read"), any(), any()))
                .thenReturn(SkillExecutionResult.toolResult(structureResult()));

        JsonNode result = runtime.execute("json_structure_read", composeArgs("/missing"),
                ToolExecutionContext.empty()).payload();

        assertEquals("skipped", result.path("composition").path("status").asText());
        assertEquals("path_not_declared", result.path("composition").path("reason").asText());
        verify(executor, never()).execute(eq("json_subtree_read"), any(), any());
    }

    @Test
    void unsupportedCompositionReturnsSourceResultWithRejectedStatus() {
        SkillExecutor executor = mock(SkillExecutor.class);
        ToolCompositionRuntime runtime = newRuntime(catalogWithReadTools("json_structure_read"), executor);
        when(executor.execute(eq("json_structure_read"), any(), any()))
                .thenReturn(SkillExecutionResult.toolResult(structureResult()));
        ObjectNode args = composeArgs("/cards");
        args.withObject("compose").put("then", "unsupported_tool");

        JsonNode result = runtime.execute("json_structure_read", args, ToolExecutionContext.empty()).payload();

        assertTrue(result.path("success").asBoolean(false));
        assertEquals("rejected", result.path("composition").path("status").asText());
        assertEquals("unsupported_composition", result.path("composition").path("reason").asText());
        assertTrue(result.path("result").path("source").path("success").asBoolean(false));
    }

    @Test
    void registryDecoratesOnlyWhitelistedSourceSchemas() {
        ToolCompositionRegistry registry = registry();
        ObjectNode schema = om.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties").putObject("file_id").put("type", "integer");
        schema.putArray("required").add("file_id");
        schema.put("additionalProperties", false);

        ObjectNode decorated = registry.decorateInputSchema("json_structure_read", schema, false);
        ObjectNode untouched = registry.decorateInputSchema("document_read", schema, false);

        assertTrue(decorated.path("properties").has("compose"));
        assertFalse(schema.path("properties").has("compose"));
        assertFalse(untouched.path("properties").has("compose"));
    }

    private ToolCompositionRuntime newRuntime(SkillCatalog catalog, SkillExecutor executor) {
        return new ToolCompositionRuntime(om, catalog, executor, registry());
    }

    private ToolCompositionRegistry registry() {
        ToolCompositionRegistry registry = new ToolCompositionRegistry(om);
        registry.init();
        return registry;
    }

    private ObjectNode structureResult() {
        ObjectNode result = om.createObjectNode();
        result.put("success", true);
        result.put("file_id", 55);
        result.put("path", "/");
        result.putArray("children").add("/cards").add("/meta");
        return result;
    }

    private ObjectNode composeArgs(String path) {
        ObjectNode args = om.createObjectNode();
        args.put("file_id", 55);
        args.put("path", "/");
        ObjectNode compose = args.putObject("compose");
        compose.put("then", "json_subtree_read");
        compose.put("when", "declared_child_path");
        ObjectNode nextArgs = compose.putObject("next_args");
        nextArgs.put("path", path);
        nextArgs.put("max_bytes", 2048);
        nextArgs.put("max_nodes", 100);
        return args;
    }

    private SkillCatalog catalogWithReadTools(String... names) {
        SkillCatalog catalog = mock(SkillCatalog.class);
        for (String name : names) {
            when(catalog.getFunction(name)).thenReturn(readFunction(name));
        }
        return catalog;
    }

    private SkillFunction readFunction(String name) {
        return new SkillFunction("test", "1", name, "", false,
                om.createObjectNode(), null, new SkillSafety(false, true));
    }
}
