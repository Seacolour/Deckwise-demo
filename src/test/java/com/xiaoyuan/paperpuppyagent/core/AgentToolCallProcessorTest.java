package com.xiaoyuan.paperpuppyagent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.xiaoyuan.paperpuppyagent.config.AgentModelCallProperties;
import com.xiaoyuan.paperpuppyagent.config.AgentToolResultProperties;
import com.xiaoyuan.paperpuppyagent.config.AgentWorkMemoryProperties;
import com.xiaoyuan.paperpuppyagent.core.composition.ToolCompositionRuntime;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentLedgerRenderer;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunLedgerStore;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunPersistenceReporter;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentToolResultStore;
import com.xiaoyuan.paperpuppyagent.core.memory.StoredToolResult;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillCatalog;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillExecutor;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillFunction;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillSafety;
import com.xiaoyuan.paperpuppyagent.core.skill.runtime.SkillExecutionResult;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.core.tool.impl.AgentLedgerUpdateTool;
import com.xiaoyuan.paperpuppyagent.core.tool.impl.AgentToolResultOpenTool;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatResponse;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolCallProcessorTest {
    private final ObjectMapper om = new ObjectMapper();

    @Test
    void processCallsEmitsToolEventsForRegularTool() {
        SkillExecutor skillExecutor = mock(SkillExecutor.class);
        AgentConversationMapper conversationMapper = new AgentConversationMapper(om);
        AgentToolCallProcessor processor = newProcessor(skillExecutor, conversationMapper, toolResultProperties(), workMemoryProperties());

        ObjectNode payload = om.createObjectNode();
        payload.put("success", true);
        payload.put("exit_code", 0);
        payload.put("stdout", "done");
        payload.put("stderr", "");
        when(skillExecutor.execute(eq("file_list"), any(), any()))
                .thenReturn(SkillExecutionResult.toolResult(payload));

        CollectingObserver observer = new CollectingObserver();
        AgentToolCallProcessor.ToolCallResult result = processor.processCalls(
                List.of(toolCall("file_list", "call-2", "{}")),
                ToolExecutionContext.empty(),
                observer,
                new AtomicBoolean(false),
                Context.ROOT);

        assertEquals(1, result.successCount());
        assertEquals(1, result.appendedInput().size());
        assertEquals(2, observer.responses.size());
        assertTrue(observer.responses.get(0).hasToolCall());
        assertTrue(observer.responses.get(1).hasToolResult());
        ArgumentCaptor<ToolExecutionContext> contextCaptor = ArgumentCaptor.forClass(ToolExecutionContext.class);
        verify(skillExecutor).execute(eq("file_list"), any(), contextCaptor.capture());
        assertEquals("call-2", contextCaptor.getValue().toolCallId());
    }

    @Test
    void processCallsKeepsImageAndFileMediaForTheNextModelTurn() {
        SkillExecutor skillExecutor = mock(SkillExecutor.class);
        AgentConversationMapper conversationMapper = new AgentConversationMapper(om);
        AgentToolCallProcessor processor = newProcessor(
                skillExecutor, conversationMapper, toolResultProperties(), workMemoryProperties());

        ObjectNode payload = om.createObjectNode();
        payload.put("success", true);
        payload.putArray("image_urls")
                .add("https://example.test/capture-1.png")
                .add("https://example.test/capture-2.png");
        payload.put("image_url", "https://example.test/capture-1.png");
        payload.put("file_url", "https://example.test/context.json");
        when(skillExecutor.execute(eq("visual_capture"), any(), any()))
                .thenReturn(SkillExecutionResult.toolResult(payload));

        AgentToolCallProcessor.ToolCallResult result = processor.processCalls(
                List.of(toolCall("visual_capture", "call-visual", "{}")),
                ToolExecutionContext.empty(),
                new CollectingObserver(),
                new AtomicBoolean(false),
                Context.ROOT);

        assertEquals(List.of(
                "https://example.test/capture-1.png",
                "https://example.test/capture-2.png"), result.imageUrls());
        assertEquals(List.of("https://example.test/context.json"), result.fileUrls());
        assertEquals(1, result.appendedInput().size());
    }

    @Test
    void processCallsDelegatesComposeArgumentsToCompositionRuntime() {
        SkillExecutor skillExecutor = mock(SkillExecutor.class);
        ToolCompositionRuntime compositionRuntime = mock(ToolCompositionRuntime.class);
        AgentConversationMapper conversationMapper = new AgentConversationMapper(om);
        AgentToolCallProcessor processor = newProcessor(skillExecutor, conversationMapper, toolResultProperties(), workMemoryProperties());
        processor.setToolCompositionRuntime(compositionRuntime);

        ObjectNode payload = om.createObjectNode();
        payload.put("success", true);
        payload.putObject("composition").put("status", "completed");
        when(compositionRuntime.hasCompose(any())).thenReturn(true);
        when(compositionRuntime.execute(eq("json_structure_read"), any(), any()))
                .thenReturn(SkillExecutionResult.toolResult(payload));

        CollectingObserver observer = new CollectingObserver();
        AgentToolCallProcessor.ToolCallResult result = processor.processCalls(
                List.of(toolCall("json_structure_read", "call-compose",
                        "{\"file_id\":297,\"path\":\"/\",\"compose\":{\"then\":\"json_subtree_read\",\"next_args\":{\"path\":\"/items\"}}}")),
                ToolExecutionContext.empty(),
                observer,
                new AtomicBoolean(false),
                Context.ROOT);

        assertEquals(1, result.successCount());
        verify(compositionRuntime).execute(eq("json_structure_read"), any(), any());
        verify(skillExecutor, never()).execute(eq("json_structure_read"), any(), any());
        assertTrue(observer.responses.get(1).getToolResult().getResultJson().contains("\"composition\""));
    }

    @Test
    void compactForModelKeepsUiResultButShrinksAppendedInput() {
        SkillExecutor skillExecutor = mock(SkillExecutor.class);
        AgentConversationMapper conversationMapper = new AgentConversationMapper(om);
        AgentToolResultProperties properties = toolResultProperties();
        properties.setModelVisibleMaxChars(500);
        properties.setTextPreviewChars(120);
        AgentWorkMemoryProperties workMemoryProperties = workMemoryProperties();
        AgentToolCallProcessor processor = newProcessor(skillExecutor, conversationMapper, properties, workMemoryProperties);

        ObjectNode payload = om.createObjectNode();
        payload.put("success", true);
        payload.put("document_id", 12);
        payload.put("content", "x".repeat(2000));
        when(skillExecutor.execute(eq("document_read"), any(), any()))
                .thenReturn(SkillExecutionResult.toolResult(payload));

        CollectingObserver observer = new CollectingObserver();
        AgentToolCallProcessor.ToolCallResult result = processor.processCalls(
                List.of(toolCall("document_read", "call-3", "{}")),
                new ToolExecutionContext(0, 0, 0, "run_test"),
                observer,
                new AtomicBoolean(false),
                Context.ROOT);

        assertEquals(1, result.appendedInput().size());
        assertTrue(observer.responses.get(1).getToolResult().getResultJson().contains("xxxx"));

        String modelVisible = result.appendedInput().get(0).asFunctionCallOutput().output().toString();
        assertTrue(modelVisible.contains("_compacted_for_model"));
        assertTrue(modelVisible.contains("content_preview"));
        assertTrue(modelVisible.contains("agent_tool_result_open"));
    }

    @Test
    void compactForModelCanBeDisabled() {
        SkillExecutor skillExecutor = mock(SkillExecutor.class);
        AgentConversationMapper conversationMapper = new AgentConversationMapper(om);
        AgentToolResultProperties properties = toolResultProperties();
        properties.setCompactForModel(false);
        properties.setModelVisibleMaxChars(10);
        AgentToolCallProcessor processor = newProcessor(skillExecutor, conversationMapper, properties, workMemoryProperties());

        ObjectNode payload = om.createObjectNode();
        payload.put("success", true);
        payload.put("content", "x".repeat(2000));

        assertEquals(payload, processor.compactForModel("document_read", payload));
    }

    @Test
    void compactRuntimeCompositionOffersJsonSubtreeOpenPath() {
        SkillExecutor skillExecutor = mock(SkillExecutor.class);
        AgentConversationMapper conversationMapper = new AgentConversationMapper(om);
        AgentToolResultProperties properties = toolResultProperties();
        properties.setModelVisibleMaxChars(500);
        AgentToolCallProcessor processor = newProcessor(skillExecutor, conversationMapper, properties, workMemoryProperties());

        ObjectNode payload = om.createObjectNode();
        payload.put("success", true);
        payload.put("padding", "x".repeat(1200));
        payload.putObject("composition").put("status", "completed").put("then", "json_subtree_read");
        ObjectNode result = payload.putObject("result");
        result.putObject("source").put("path", "/items");
        result.putObject("next").putObject("value").put("title", "Example");
        JsonNode compact = processor.compactForModel(
                "json_structure_read",
                payload,
                new StoredToolResult("run", "call-compose", "json_structure_read", payload,
                        payload.toString(), payload.toString().length(), Instant.now()));

        assertTrue(compact.path("_compacted_for_model").asBoolean(false));
        assertEquals("/result/next/value",
                compact.path("raw_result_open_paths").path("recommended").path("path").asText());
        assertTrue(compact.path("next_step_hint").asText().contains("/result/next/value"));
    }

    @Test
    void runtimeCompositionUsesStepScaledCompactionThreshold() {
        SkillExecutor skillExecutor = mock(SkillExecutor.class);
        AgentConversationMapper conversationMapper = new AgentConversationMapper(om);
        AgentToolResultProperties properties = toolResultProperties();
        properties.setModelVisibleMaxChars(1000);
        AgentToolCallProcessor processor = newProcessor(skillExecutor, conversationMapper, properties, workMemoryProperties());

        ObjectNode twoStepPayload = compositionPayloadWithSteps(2, 1400);
        JsonNode twoStepVisible = processor.compactForModel("json_structure_read", twoStepPayload);
        assertEquals(twoStepPayload, twoStepVisible);

        ObjectNode oneStepPayload = compositionPayloadWithSteps(1, 1400);
        JsonNode oneStepVisible = processor.compactForModel("json_structure_read", oneStepPayload);
        assertTrue(oneStepVisible.path("_compacted_for_model").asBoolean(false));

        ObjectNode threeStepPayload = compositionPayloadWithSteps(3, 2300);
        JsonNode threeStepVisible = processor.compactForModel("json_structure_read", threeStepPayload);
        assertEquals(threeStepPayload, threeStepVisible);
    }

    @Test
    void invalidToolArgumentsReturnExplicitErrorWithoutExecutingTool() {
        SkillExecutor skillExecutor = mock(SkillExecutor.class);
        AgentConversationMapper conversationMapper = new AgentConversationMapper(om);
        AgentToolCallProcessor processor = newProcessor(
                skillExecutor,
                conversationMapper,
                toolResultProperties(),
                workMemoryProperties());

        CollectingObserver observer = new CollectingObserver();
        AgentToolCallProcessor.ToolCallResult result = processor.processCalls(
                List.of(toolCall("json_structure_read", "call-bad-json",
                        "{\"file_id\":101,\"path\":\"/sections\"")),
                ToolExecutionContext.empty(),
                observer,
                new AtomicBoolean(false),
                Context.ROOT);

        assertEquals(1, result.errorCount());
        assertEquals(0, result.successCount());
        assertEquals("INVALID_TOOL_ARGUMENTS", result.lastErrorCode());
        assertEquals(1, result.appendedInput().size());
        String toolResultJson = observer.responses.get(1).getToolResult().getResultJson();
        assertTrue(toolResultJson.contains("INVALID_TOOL_ARGUMENTS"));
        assertTrue(toolResultJson.contains("arguments_preview"));
        String modelVisible = result.appendedInput().get(0).asFunctionCallOutput().output().toString();
        assertTrue(modelVisible.contains("INVALID_TOOL_ARGUMENTS"));
        assertTrue(modelVisible.contains("max_output_tokens"));
        verify(skillExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void agentToolResultOpenReadsStoredResultByCallIdAndPath() {
        AgentWorkMemoryProperties workMemoryProperties = workMemoryProperties();
        AgentToolResultStore store = new AgentToolResultStore(om, workMemoryProperties);

        ObjectNode payload = om.createObjectNode();
        payload.put("success", true);
        payload.put("content", "stored content");
        store.store("run_open", "call-open", "document_read", payload);

        AgentToolResultOpenTool tool = new AgentToolResultOpenTool(om, store, workMemoryProperties);
        ObjectNode args = om.createObjectNode();
        args.put("call_id", "call-open");
        args.put("mode", "path");
        args.put("path", "/content");

        JsonNode opened = tool.execute(args, new ToolExecutionContext(0, 0, 0, "run_open"));

        assertTrue(opened.path("success").asBoolean(false));
        assertEquals("stored content", opened.path("value").asText());
    }

    @Test
    void toolCallsAreRecordedIntoRunLedger() {
        SkillExecutor skillExecutor = mock(SkillExecutor.class);
        AgentConversationMapper conversationMapper = new AgentConversationMapper(om);
        AgentWorkMemoryProperties workMemoryProperties = workMemoryProperties();
        AgentToolResultStore toolResultStore = new AgentToolResultStore(om, workMemoryProperties);
        AgentRunLedgerStore ledgerStore = new AgentRunLedgerStore(om, workMemoryProperties);
        ledgerStore.createRun("run_ledger", "update document");
        AgentToolCallProcessor processor = new AgentToolCallProcessor(
                om,
                new AgentModelCallProperties(),
                mock(SkillCatalog.class),
                skillExecutor,
                conversationMapper,
                toolResultProperties(),
                toolResultStore,
                ledgerStore,
                mock(AgentRunPersistenceReporter.class));

        ObjectNode payload = om.createObjectNode();
        payload.put("success", true);
        payload.put("document_id", 95);
        payload.put("document_type", "markdown");
        payload.put("revision", "rev-1");
        when(skillExecutor.execute(eq("document_read"), any(), any()))
                .thenReturn(SkillExecutionResult.toolResult(payload));

        processor.processCalls(
                List.of(toolCall("document_read", "call-ledger", "{}")),
                new ToolExecutionContext(0, 0, 0, "run_ledger"),
                new CollectingObserver(),
                new AtomicBoolean(false),
                Context.ROOT);

        var snapshot = ledgerStore.snapshot("run_ledger").orElseThrow();
        assertEquals(1, snapshot.recentToolCalls().size());
        assertEquals("call-ledger", snapshot.recentToolCalls().get(0).callId());
        assertEquals(1, snapshot.artifacts().size());
        assertEquals(95L, snapshot.artifacts().get(0).documentId());
    }

    @Test
    void duplicateReadAddsReuseHintToLedgerAndModelVisibleResult() {
        SkillExecutor skillExecutor = mock(SkillExecutor.class);
        AgentConversationMapper conversationMapper = new AgentConversationMapper(om);
        AgentWorkMemoryProperties workMemoryProperties = workMemoryProperties();
        AgentToolResultStore toolResultStore = new AgentToolResultStore(om, workMemoryProperties);
        AgentRunLedgerStore ledgerStore = new AgentRunLedgerStore(om, workMemoryProperties);
        ledgerStore.createRun("run_duplicate", "inspect same subtree");
        AgentToolCallProcessor processor = new AgentToolCallProcessor(
                om,
                new AgentModelCallProperties(),
                mock(SkillCatalog.class),
                skillExecutor,
                conversationMapper,
                toolResultProperties(),
                toolResultStore,
                ledgerStore,
                mock(AgentRunPersistenceReporter.class));

        ObjectNode payload = om.createObjectNode();
        payload.put("success", true);
        payload.put("document_id", 95);
        payload.put("path", "/sections/0");
        payload.put("revision", "rev-1");
        when(skillExecutor.execute(eq("json_subtree_read"), any(), any()))
                .thenReturn(SkillExecutionResult.toolResult(payload));

        AgentToolCallProcessor.ToolCallResult result = processor.processCalls(
                List.of(
                        toolCall("json_subtree_read", "call-first", "{\"document_id\":95,\"path\":\"/sections/0\"}"),
                        toolCall("json_subtree_read", "call-second", "{\"document_id\":95,\"path\":\"/sections/0\"}")),
                new ToolExecutionContext(0, 0, 0, "run_duplicate"),
                new CollectingObserver(),
                new AtomicBoolean(false),
                Context.ROOT);

        String secondModelVisible = result.appendedInput().get(1).asFunctionCallOutput().output().toString();
        assertTrue(secondModelVisible.contains("read_reuse_hint"));
        assertTrue(secondModelVisible.contains("call-first"));

        String ledgerView = new AgentLedgerRenderer(om, workMemoryProperties, ledgerStore)
                .render("run_duplicate")
                .orElseThrow();
        assertTrue(ledgerView.contains("readReuseHints"));
        assertTrue(ledgerView.contains("call-second"));
        assertTrue(ledgerView.contains("call-first"));
    }

    @Test
    void multipleWriteToolsInOneResponseOnlyExecuteFirstWrite() {
        SkillExecutor skillExecutor = mock(SkillExecutor.class);
        SkillCatalog skillCatalog = mock(SkillCatalog.class);
        when(skillCatalog.getFunction("document_save")).thenReturn(writeFunction("document_save"));
        AgentConversationMapper conversationMapper = new AgentConversationMapper(om);
        AgentToolCallProcessor processor = new AgentToolCallProcessor(
                om,
                new AgentModelCallProperties(),
                skillCatalog,
                skillExecutor,
                conversationMapper,
                toolResultProperties(),
                new AgentToolResultStore(om, workMemoryProperties()),
                new AgentRunLedgerStore(om, workMemoryProperties()),
                mock(AgentRunPersistenceReporter.class));

        ObjectNode payload = om.createObjectNode();
        payload.put("success", true);
        payload.put("revision", "rev-2");
        when(skillExecutor.execute(eq("document_save"), any(), any()))
                .thenReturn(SkillExecutionResult.toolResult(payload));

        CollectingObserver observer = new CollectingObserver();
        AgentToolCallProcessor.ToolCallResult result = processor.processCalls(
                List.of(
                        toolCall("document_save", "call-write-1", "{\"document_id\":101}"),
                        toolCall("document_save", "call-write-2", "{\"document_id\":101}")),
                ToolExecutionContext.empty(),
                observer,
                new AtomicBoolean(false),
                Context.ROOT);

        assertEquals(1, result.successCount());
        assertEquals(1, result.errorCount());
        verify(skillExecutor).execute(eq("document_save"), any(), any());
        String secondModelVisible = result.appendedInput().get(1).asFunctionCallOutput().output().toString();
        assertTrue(secondModelVisible.contains("SEQUENTIAL_WRITE_REQUIRED"));
        assertTrue(secondModelVisible.contains("parallel_write_blocked"));
    }

    @Test
    void agentLedgerUpdateWritesFactsAndRendererShowsView() {
        AgentWorkMemoryProperties workMemoryProperties = workMemoryProperties();
        AgentToolResultStore toolResultStore = new AgentToolResultStore(om, workMemoryProperties);
        AgentRunLedgerStore ledgerStore = new AgentRunLedgerStore(om, workMemoryProperties);
        ledgerStore.createRun("run_update", "change title color");

        ObjectNode source = om.createObjectNode();
        source.put("success", true);
        toolResultStore.store("run_update", "call-source", "json_subtree_read", source);

        AgentLedgerUpdateTool tool = new AgentLedgerUpdateTool(om, ledgerStore, toolResultStore, workMemoryProperties);
        ObjectNode args = om.createObjectNode();
        var updates = args.putArray("updates");
        ObjectNode todo = updates.addObject();
        todo.put("op", "add_todo");
        todo.put("id", "todo_color");
        todo.put("text", "patch title textColor mark");
        ObjectNode fact = updates.addObject();
        fact.put("op", "add_fact");
        fact.put("text", "title node supports marks.textColor");
        fact.put("sourceCallId", "call-source");

        JsonNode result = tool.execute(args, new ToolExecutionContext(0, 0, 0, "run_update"));

        assertTrue(result.path("success").asBoolean(false));
        assertEquals(2, result.path("applied_count").asInt());
        AgentLedgerRenderer renderer = new AgentLedgerRenderer(om, workMemoryProperties, ledgerStore);
        String view = renderer.render("run_update").orElseThrow();
        assertTrue(view.contains("patch title textColor mark"));
        assertTrue(view.contains("title node supports marks.textColor"));
    }

    @Test
    void ledgerRendererShowsBudgetWarnings() {
        AgentWorkMemoryProperties workMemoryProperties = workMemoryProperties();
        workMemoryProperties.setSoftMaxModelCallsPerRun(2);
        AgentRunLedgerStore ledgerStore = new AgentRunLedgerStore(om, workMemoryProperties);
        ledgerStore.createRun("run_budget", "finish compactly");
        ledgerStore.recordModelCall("run_budget");
        ledgerStore.recordModelCall("run_budget");

        String view = new AgentLedgerRenderer(om, workMemoryProperties, ledgerStore)
                .render("run_budget")
                .orElseThrow();

        assertTrue(view.contains("\"budget\""));
        assertTrue(view.contains("soft_limit_reached"));
        assertTrue(view.contains("modelCalls reached soft limit"));
    }

    private AgentToolResultProperties toolResultProperties() {
        AgentToolResultProperties properties = new AgentToolResultProperties();
        properties.setCompactForModel(true);
        return properties;
    }

    private AgentWorkMemoryProperties workMemoryProperties() {
        AgentWorkMemoryProperties properties = new AgentWorkMemoryProperties();
        properties.setEnabled(true);
        return properties;
    }

    private AgentToolCallProcessor newProcessor(SkillExecutor skillExecutor,
                                                AgentConversationMapper conversationMapper,
                                                AgentToolResultProperties toolResultProperties,
                                                AgentWorkMemoryProperties workMemoryProperties) {
        return new AgentToolCallProcessor(
                om,
                new AgentModelCallProperties(),
                mock(SkillCatalog.class),
                skillExecutor,
                conversationMapper,
                toolResultProperties,
                new AgentToolResultStore(om, workMemoryProperties),
                new AgentRunLedgerStore(om, workMemoryProperties),
                mock(AgentRunPersistenceReporter.class));
    }

    private ObjectNode compositionPayloadWithSteps(int stepsCount, int payloadChars) {
        ObjectNode payload = om.createObjectNode();
        payload.put("success", true);
        payload.put("padding", "x".repeat(payloadChars));
        ObjectNode composition = payload.putObject("composition");
        composition.put("attempted", true);
        composition.put("status", stepsCount > 1 ? "completed" : "skipped");
        composition.put("source", "json_structure_read");
        composition.put("then", "json_subtree_read");
        ArrayNode steps = payload.putArray("steps");
        for (int i = 0; i < stepsCount; i++) {
            steps.addObject()
                    .put("tool", i == 0 ? "json_structure_read" : "json_subtree_read")
                    .put("success", true);
        }
        ObjectNode result = payload.putObject("result");
        result.putObject("source").put("success", true);
        if (stepsCount > 1) {
            ObjectNode next = result.putObject("next");
            next.put("success", true);
            next.put("file_id", 297);
            next.putObject("value").put("title", "Example");
        }
        return payload;
    }

    private SkillFunction writeFunction(String name) {
        return new SkillFunction(
                "test",
                "1",
                name,
                "",
                false,
                om.createObjectNode(),
                null,
                new SkillSafety(true, false));
    }

    private ResponseFunctionToolCall toolCall(String name, String callId, String arguments) {
        return ResponseFunctionToolCall.builder()
                .name(name)
                .callId(callId)
                .arguments(arguments)
                .build();
    }

    private static final class CollectingObserver implements StreamObserver<AgentChatResponse> {
        private final List<AgentChatResponse> responses = new ArrayList<>();

        @Override
        public void onNext(AgentChatResponse value) {
            responses.add(value);
        }

        @Override
        public void onError(Throwable t) {
        }

        @Override
        public void onCompleted() {
        }
    }
}
