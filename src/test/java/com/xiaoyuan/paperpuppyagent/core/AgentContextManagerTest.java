package com.xiaoyuan.paperpuppyagent.core;

import com.openai.core.JsonValue;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseCompactionItem;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseReasoningItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentContextManagerTest {

    @Test
    void nextTurnInputKeepsReasoningItemsForReasoningModels() {
        ResponseReasoningItem reasoning = ResponseReasoningItem.builder()
                .id("rs_test")
                .summary(List.of())
                .build();
        ResponseFunctionToolCall functionCall = ResponseFunctionToolCall.builder()
                .callId("call_test")
                .name("agent_tool_result_open")
                .arguments("{}")
                .build();

        List<ResponseInputItem> inputItems = AgentContextManager.toNextTurnInputItems(List.of(
                ResponseOutputItem.ofReasoning(reasoning),
                ResponseOutputItem.ofFunctionCall(functionCall)));

        assertEquals(2, inputItems.size());
        assertTrue(inputItems.get(0).isReasoning());
        assertEquals("rs_test", inputItems.get(0).asReasoning().id());
        assertTrue(inputItems.get(1).isFunctionCall());
        assertEquals("call_test", inputItems.get(1).asFunctionCall().callId());
    }

    @Test
    void nextTurnInputKeepsCompactionItemsForFutureRequests() {
        ResponseCompactionItem compaction = ResponseCompactionItem.builder()
                .id("cmp_test")
                .type(JsonValue.from("compaction"))
                .encryptedContent("encrypted-test")
                .build();

        List<ResponseInputItem> inputItems = AgentContextManager.toNextTurnInputItems(List.of(
                ResponseOutputItem.ofCompaction(compaction)));

        assertEquals(1, inputItems.size());
        assertTrue(inputItems.get(0).isCompaction());
        assertEquals("cmp_test", inputItems.get(0).asCompaction().id().orElse(""));
        assertEquals("encrypted-test", inputItems.get(0).asCompaction().encryptedContent());
    }

    @Test
    void nextTurnInputDropsAssistantMessagesByDefault() {
        ResponseReasoningItem reasoning = ResponseReasoningItem.builder()
                .id("rs_test")
                .summary(List.of())
                .build();
        ResponseOutputItem message = mock(ResponseOutputItem.class);
        when(message.isMessage()).thenReturn(true);
        ResponseFunctionToolCall functionCall = ResponseFunctionToolCall.builder()
                .callId("call_test")
                .name("json_document_patch")
                .arguments("{}")
                .build();

        List<ResponseInputItem> inputItems = AgentContextManager.toNextTurnInputItems(List.of(
                ResponseOutputItem.ofReasoning(reasoning),
                message,
                ResponseOutputItem.ofFunctionCall(functionCall)));

        assertEquals(2, inputItems.size());
        assertTrue(inputItems.get(0).isReasoning());
        assertTrue(inputItems.get(1).isFunctionCall());
    }

    @Test
    void compactLoopHistoryPrunesOldCompletedToolTurnsOnlyInCurrentRun() {
        ArrayList<ResponseInputItem> input = new ArrayList<>();
        input.add(reasoningInput("rs_history"));
        input.add(functionCallInput("call-history"));
        input.add(functionOutputInput("call-history"));
        input.add(easySystemInput("current user message boundary"));
        int currentRunStart = input.size();

        input.add(reasoningInput("rs_1"));
        input.add(functionCallInput("call-1"));
        input.add(functionOutputInput("call-1"));
        ResponseInputItem systemHint = easySystemInput("keep correction hint");
        input.add(systemHint);
        input.add(reasoningInput("rs_2"));
        input.add(functionCallInput("call-2"));
        input.add(functionOutputInput("call-2"));
        input.add(reasoningInput("rs_3"));
        input.add(functionCallInput("call-3"));
        input.add(functionOutputInput("call-3"));

        AgentContextManager.LoopHistoryCompactionResult result =
                AgentContextManager.compactLoopHistory(input, currentRunStart, 2);
        List<ResponseInputItem> compacted = result.inputItems();

        assertEquals(3, result.removedItems());
        assertEquals(1, result.removedToolTurns());
        assertEquals(input.get(0), compacted.get(0));
        assertTrue(hasFunctionCall(compacted, "call-history"));
        assertTrue(hasFunctionOutput(compacted, "call-history"));
        assertTrue(compacted.contains(systemHint));
        assertFalse(hasReasoning(compacted, "rs_1"));
        assertFalse(hasFunctionCall(compacted, "call-1"));
        assertFalse(hasFunctionOutput(compacted, "call-1"));
        assertTrue(hasReasoning(compacted, "rs_2"));
        assertTrue(hasFunctionCall(compacted, "call-2"));
        assertTrue(hasFunctionOutput(compacted, "call-2"));
        assertTrue(hasReasoning(compacted, "rs_3"));
        assertTrue(hasFunctionCall(compacted, "call-3"));
        assertTrue(hasFunctionOutput(compacted, "call-3"));
        assertDoesNotThrow(() -> compacted.add(easySystemInput("next loop can append")));
    }

    @Test
    void compactLoopHistoryDoesNotOrphanRetainedToolPairs() {
        ArrayList<ResponseInputItem> input = new ArrayList<>();
        input.add(easySystemInput("prefix"));
        int currentRunStart = input.size();
        for (int i = 1; i <= 4; i++) {
            input.add(reasoningInput("rs_" + i));
            input.add(functionCallInput("call-" + i));
            input.add(functionOutputInput("call-" + i));
        }

        AgentContextManager.LoopHistoryCompactionResult result =
                AgentContextManager.compactLoopHistory(input, currentRunStart, 2);
        List<ResponseInputItem> compacted = result.inputItems();

        assertEquals(Set.of("call-3", "call-4"), functionCallIds(compacted));
        assertEquals(functionCallIds(compacted), functionOutputCallIds(compacted));
    }

    @Test
    void compactLoopHistoryKeepsIncompleteToolExchange() {
        ArrayList<ResponseInputItem> input = new ArrayList<>();
        input.add(easySystemInput("prefix"));
        int currentRunStart = input.size();
        input.add(reasoningInput("rs_done"));
        input.add(functionCallInput("call-done"));
        input.add(functionOutputInput("call-done"));
        input.add(reasoningInput("rs_pending"));
        input.add(functionCallInput("call-pending"));

        AgentContextManager.LoopHistoryCompactionResult result =
                AgentContextManager.compactLoopHistory(input, currentRunStart, 0);
        List<ResponseInputItem> compacted = result.inputItems();

        assertFalse(hasFunctionCall(compacted, "call-done"));
        assertFalse(hasFunctionOutput(compacted, "call-done"));
        assertTrue(hasReasoning(compacted, "rs_pending"));
        assertTrue(hasFunctionCall(compacted, "call-pending"));
    }

    @Test
    void officialCompactionPrunesEverythingBeforeLatestCompaction() {
        ArrayList<ResponseInputItem> input = new ArrayList<>();
        input.add(easySystemInput("old user-visible context"));
        input.add(functionCallInput("call-old"));
        input.add(compactionInput("cmp-latest"));
        input.add(functionCallInput("call-new"));
        input.add(functionOutputInput("call-new"));

        AgentContextManager.OfficialContextCompactionResult result =
                AgentContextManager.compactAroundLatestOfficialCompaction(input);

        assertEquals(2, result.removedItems());
        assertEquals(5, result.originalItems());
        assertEquals(3, result.compactedItems());
        assertTrue(result.inputItems().get(0).isCompaction());
        assertFalse(hasFunctionCall(result.inputItems(), "call-old"));
        assertTrue(hasFunctionCall(result.inputItems(), "call-new"));
        assertTrue(hasFunctionOutput(result.inputItems(), "call-new"));
        assertTrue(result.estimatedSavedChars() > 0);
        assertFalse(result.inputFingerprint().isBlank());
    }

    private static ResponseInputItem reasoningInput(String id) {
        return ResponseInputItem.ofReasoning(ResponseReasoningItem.builder()
                .id(id)
                .summary(List.of())
                .build());
    }

    private static ResponseInputItem functionCallInput(String callId) {
        return ResponseInputItem.ofFunctionCall(ResponseFunctionToolCall.builder()
                .callId(callId)
                .name("json_document_read")
                .arguments("{}")
                .build());
    }

    private static ResponseInputItem functionOutputInput(String callId) {
        return ResponseInputItem.ofFunctionCallOutput(ResponseInputItem.FunctionCallOutput.builder()
                .callId(callId)
                .output("{\"success\":true}")
                .build());
    }

    private static ResponseInputItem compactionInput(String id) {
        return ResponseInputItem.ofCompaction(com.openai.models.responses.ResponseCompactionItemParam.builder()
                .id(id)
                .type(JsonValue.from("compaction"))
                .encryptedContent("encrypted-" + id)
                .build());
    }

    private static ResponseInputItem easySystemInput(String text) {
        return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(EasyInputMessage.Role.SYSTEM)
                .content(text)
                .build());
    }

    private static boolean hasReasoning(List<ResponseInputItem> items, String id) {
        return items.stream()
                .anyMatch(item -> item.isReasoning() && id.equals(item.asReasoning().id()));
    }

    private static boolean hasFunctionCall(List<ResponseInputItem> items, String callId) {
        return items.stream()
                .anyMatch(item -> item.isFunctionCall() && callId.equals(item.asFunctionCall().callId()));
    }

    private static boolean hasFunctionOutput(List<ResponseInputItem> items, String callId) {
        return items.stream()
                .anyMatch(item -> item.isFunctionCallOutput() && callId.equals(item.asFunctionCallOutput().callId()));
    }

    private static Set<String> functionCallIds(List<ResponseInputItem> items) {
        Set<String> out = new HashSet<>();
        for (ResponseInputItem item : items) {
            if (item.isFunctionCall()) {
                out.add(item.asFunctionCall().callId());
            }
        }
        return out;
    }

    private static Set<String> functionOutputCallIds(List<ResponseInputItem> items) {
        Set<String> out = new HashSet<>();
        for (ResponseInputItem item : items) {
            if (item.isFunctionCallOutput()) {
                out.add(item.asFunctionCallOutput().callId());
            }
        }
        return out;
    }
}
