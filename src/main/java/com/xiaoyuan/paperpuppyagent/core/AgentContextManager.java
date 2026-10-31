package com.xiaoyuan.paperpuppyagent.core;

import com.openai.core.JsonValue;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCompactionItem;
import com.openai.models.responses.ResponseCompactionItemParam;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.xiaoyuan.paperpuppyagent.config.AgentOpenAiContextManagementProperties;
import com.xiaoyuan.paperpuppyagent.config.AgentWorkMemoryProperties;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentLedgerRenderer;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunPersistenceReporter;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Owns model input replay, ledger injection, and context compaction policies. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentContextManager {
    private final AgentOpenAiContextManagementProperties openAiContextManagementProperties;
    private final AgentWorkMemoryProperties workMemoryProperties;
    private final AgentConversationMapper conversationMapper;
    private final AgentLedgerRenderer ledgerRenderer;
    private final AgentRunPersistenceReporter persistenceReporter;

    public List<ResponseInputItem> withLedgerView(String agentRunId, List<ResponseInputItem> input) {
        return ledgerRenderer.render(agentRunId)
                .map(view -> {
                    ArrayList<ResponseInputItem> out = new ArrayList<>(input.size() + 1);
                    out.addAll(input);
                    out.add(conversationMapper.systemMessageItem(view));
                    return List.copyOf(out);
                })
                .orElse(input);
    }

    public List<ResponseInputItem> compactLoopHistoryIfEnabled(String sessionKey,
                                                               List<ResponseInputItem> input,
                                                               int loopHistoryStartIndex) {
        if (!workMemoryProperties.isEnabled() || !workMemoryProperties.isLoopHistoryCompactionEnabled()) {
            return input;
        }
        LoopHistoryCompactionResult result = compactLoopHistory(
                input,
                loopHistoryStartIndex,
                workMemoryProperties.getRetainedToolTurnsInLoopHistory());
        if (result.removedItems() > 0) {
            log.info(
                    "[Agent][Loop] loop history compacted: sessionKey={}, inputItems {}->{}, removedItems={}, removedToolTurns={}, retainedToolTurns={}, approxSavedChars={}",
                    sessionKey,
                    result.originalItems(),
                    result.compactedItems(),
                    result.removedItems(),
                    result.removedToolTurns(),
                    result.retainedToolTurns(),
                    result.approxSavedChars());
        }
        return result.inputItems();
    }

    public void persistOfficialContextCompactionIfNeeded(String agentRunId,
                                                         AgentChatRequest request,
                                                         String model,
                                                         String instructions,
                                                         Response response,
                                                         OfficialContextCompactionResult compaction,
                                                         List<ResponseInputItem> compactedInput,
                                                         String callSite) {
        if (compaction == null
                || !openAiContextManagementProperties.isEnabled()
                || !openAiContextManagementProperties.isPersistCompactedWindow()) {
            return;
        }
        persistenceReporter.recordContextCompaction(
                agentRunId,
                request,
                model,
                instructions,
                openAiContextManagementProperties.getCompactThreshold(),
                response,
                compactedInput,
                compaction.sourceFirstItemIdx(),
                compaction.sourceLastItemIdx(),
                compaction.estimatedInputChars(),
                compaction.estimatedSavedChars(),
                compaction.inputFingerprint(),
                callSite);
    }

    public boolean officialContextManagementEnabled() {
        return openAiContextManagementProperties.isEnabled();
    }

    public boolean retainAssistantMessages() {
        return workMemoryProperties.isRetainAssistantMessagesInLoopHistory();
    }

    public static boolean containsCompaction(List<ResponseInputItem> inputItems) {
        if (inputItems == null || inputItems.isEmpty()) {
            return false;
        }
        for (ResponseInputItem item : inputItems) {
            if (item != null && item.isCompaction()) {
                return true;
            }
        }
        return false;
    }

    public static OfficialContextCompactionResult compactAroundLatestOfficialCompaction(
            List<ResponseInputItem> inputItems) {
        List<ResponseInputItem> input = inputItems == null ? List.of() : inputItems;
        int latestCompactionIndex = -1;
        for (int i = 0; i < input.size(); i++) {
            ResponseInputItem item = input.get(i);
            if (item != null && item.isCompaction()) {
                latestCompactionIndex = i;
            }
        }
        if (latestCompactionIndex < 0) {
            return null;
        }

        int estimatedInputChars = 0;
        int estimatedSavedChars = 0;
        for (int i = 0; i < input.size(); i++) {
            int chars = approxInputItemChars(input.get(i));
            estimatedInputChars = saturatedAdd(estimatedInputChars, chars);
            if (i < latestCompactionIndex) {
                estimatedSavedChars = saturatedAdd(estimatedSavedChars, chars);
            }
        }

        ArrayList<ResponseInputItem> compacted = new ArrayList<>(
                input.subList(latestCompactionIndex, input.size()));
        return new OfficialContextCompactionResult(
                compacted,
                input.size(),
                compacted.size(),
                0,
                Math.max(latestCompactionIndex - 1, 0),
                latestCompactionIndex,
                estimatedInputChars,
                estimatedSavedChars,
                sha256Hex(Objects.toString(input, "")));
    }

    public static List<ResponseFunctionToolCall> findFunctionCalls(List<ResponseOutputItem> outputItems) {
        List<ResponseFunctionToolCall> out = new ArrayList<>();
        for (ResponseOutputItem item : outputItems) {
            if (item != null && item.isFunctionCall()) {
                out.add(item.asFunctionCall());
            }
        }
        return out;
    }

    public static List<ResponseInputItem> toNextTurnInputItems(List<ResponseOutputItem> outputItems) {
        return toNextTurnInputItems(outputItems, false);
    }

    public static List<ResponseInputItem> toNextTurnInputItems(List<ResponseOutputItem> outputItems,
                                                              boolean retainAssistantMessages) {
        List<ResponseInputItem> out = new ArrayList<>();
        if (outputItems == null || outputItems.isEmpty()) {
            return out;
        }
        for (ResponseOutputItem item : outputItems) {
            if (item == null) {
                continue;
            }
            if (item.isReasoning()) {
                out.add(ResponseInputItem.ofReasoning(item.asReasoning()));
            } else if (item.isMessage() && retainAssistantMessages) {
                out.add(ResponseInputItem.ofResponseOutputMessage(item.asMessage()));
            } else if (item.isFunctionCall()) {
                out.add(ResponseInputItem.ofFunctionCall(item.asFunctionCall()));
            } else if (item.isWebSearchCall()) {
                out.add(ResponseInputItem.ofWebSearchCall(item.asWebSearchCall()));
            } else if (item.isCompaction()) {
                ResponseInputItem compaction = compactionInputItem(item.asCompaction());
                if (compaction != null) {
                    out.add(compaction);
                }
            }
        }
        return out;
    }

    private static ResponseInputItem compactionInputItem(ResponseCompactionItem item) {
        if (item == null || Objects.toString(item.encryptedContent(), "").isBlank()) {
            return null;
        }
        ResponseCompactionItemParam.Builder builder = ResponseCompactionItemParam.builder()
                .type(JsonValue.from("compaction"))
                .encryptedContent(item.encryptedContent());
        String id = Objects.toString(item.id(), "").trim();
        if (!id.isBlank()) {
            builder.id(id);
        }
        return ResponseInputItem.ofCompaction(builder.build());
    }

    public static LoopHistoryCompactionResult compactLoopHistory(List<ResponseInputItem> inputItems,
                                                                 int loopHistoryStartIndex,
                                                                 int retainedToolTurns) {
        List<ResponseInputItem> input = inputItems == null ? List.of() : inputItems;
        int start = Math.min(Math.max(loopHistoryStartIndex, 0), input.size());
        int retain = Math.max(retainedToolTurns, 0);
        List<ToolExchangeBlock> blocks = completedToolExchangeBlocks(input, start);
        int removeBlockCount = Math.max(blocks.size() - retain, 0);
        if (removeBlockCount <= 0) {
            return new LoopHistoryCompactionResult(
                    new ArrayList<>(input), input.size(), input.size(), 0, 0, blocks.size(), 0);
        }

        Set<Integer> removeIndexes = new HashSet<>();
        int approxSavedChars = 0;
        for (int i = 0; i < removeBlockCount; i++) {
            ToolExchangeBlock block = blocks.get(i);
            for (int itemIndex = block.startInclusive(); itemIndex < block.endExclusive(); itemIndex++) {
                if (removeIndexes.add(itemIndex)) {
                    approxSavedChars += approxInputItemChars(input.get(itemIndex));
                }
            }
        }

        ArrayList<ResponseInputItem> compacted = new ArrayList<>(Math.max(input.size() - removeIndexes.size(), 0));
        for (int i = 0; i < input.size(); i++) {
            if (!removeIndexes.contains(i)) {
                compacted.add(input.get(i));
            }
        }
        return new LoopHistoryCompactionResult(
                compacted,
                input.size(),
                compacted.size(),
                removeIndexes.size(),
                removeBlockCount,
                blocks.size() - removeBlockCount,
                approxSavedChars);
    }

    private static List<ToolExchangeBlock> completedToolExchangeBlocks(List<ResponseInputItem> input, int start) {
        ArrayList<ToolExchangeBlock> blocks = new ArrayList<>();
        int i = start;
        while (i < input.size()) {
            ResponseInputItem item = input.get(i);
            if (!isModelOutputReplayItem(item)) {
                i++;
                continue;
            }

            int blockStart = i;
            Set<String> functionCallIds = new HashSet<>();
            while (i < input.size() && isModelOutputReplayItem(input.get(i))) {
                ResponseInputItem replayItem = input.get(i);
                if (replayItem.isFunctionCall()) {
                    String callId = normalizeCallId(replayItem.asFunctionCall().callId());
                    if (!callId.isBlank()) {
                        functionCallIds.add(callId);
                    }
                }
                i++;
            }

            if (functionCallIds.isEmpty()) {
                continue;
            }

            Set<String> outputCallIds = new HashSet<>();
            while (i < input.size() && input.get(i).isFunctionCallOutput()) {
                String callId = normalizeCallId(input.get(i).asFunctionCallOutput().callId());
                if (!callId.isBlank()) {
                    outputCallIds.add(callId);
                }
                i++;
            }

            if (!outputCallIds.isEmpty() && functionCallIds.containsAll(outputCallIds)) {
                blocks.add(new ToolExchangeBlock(blockStart, i));
            }
        }
        return blocks;
    }

    private static boolean isModelOutputReplayItem(ResponseInputItem item) {
        return item != null
                && (item.isReasoning()
                || item.isResponseOutputMessage()
                || item.isFunctionCall()
                || item.isWebSearchCall());
    }

    private static int approxInputItemChars(ResponseInputItem item) {
        return Objects.toString(item, "").length();
    }

    private static int saturatedAdd(int left, int right) {
        long value = (long) Math.max(left, 0) + Math.max(right, 0);
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static String sha256Hex(String value) {
        String text = Objects.toString(value, "");
        if (text.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    private static String normalizeCallId(String callId) {
        return Objects.toString(callId, "").trim();
    }

    public record LoopHistoryCompactionResult(
            List<ResponseInputItem> inputItems,
            int originalItems,
            int compactedItems,
            int removedItems,
            int removedToolTurns,
            int retainedToolTurns,
            int approxSavedChars) {
    }

    public record OfficialContextCompactionResult(
            List<ResponseInputItem> inputItems,
            int originalItems,
            int compactedItems,
            int sourceFirstItemIdx,
            int sourceLastItemIdx,
            int removedItems,
            int estimatedInputChars,
            int estimatedSavedChars,
            String inputFingerprint) {
    }

    private record ToolExchangeBlock(int startInclusive, int endExclusive) {
    }
}
