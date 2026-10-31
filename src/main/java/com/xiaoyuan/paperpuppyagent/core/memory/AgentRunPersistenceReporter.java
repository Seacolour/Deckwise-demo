package com.xiaoyuan.paperpuppyagent.core.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openai.models.responses.ResponseCompactionItemParam;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseUsage;
import com.openai.models.responses.Tool;
import com.xiaoyuan.paperpuppyagent.config.AgentWorkMemoryProperties;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatRequest;
import com.xiaoyuan.paperpuppyagent.grpc.backend.AgentTokenUsage;
import com.xiaoyuan.paperpuppyagent.grpc.backend.FinishAgentRunRequest;
import com.xiaoyuan.paperpuppyagent.grpc.backend.RecordAgentContextCompactionRequest;
import com.xiaoyuan.paperpuppyagent.grpc.backend.RecordAgentModelCallRequest;
import com.xiaoyuan.paperpuppyagent.grpc.backend.RecordAgentToolCallRequest;
import com.xiaoyuan.paperpuppyagent.grpc.backend.UpsertAgentLedgerSnapshotRequest;
import com.xiaoyuan.paperpuppyagent.grpc.backend.UpsertAgentRunRequest;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 阶段三 A：把 Agent run 的关键事实 best-effort 上报给 backend 持久化。
 *
 * <p>失败只记录日志，不影响当前 Agent loop 主行为。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRunPersistenceReporter {

    private final BackendToolGrpcClient backendToolGrpcClient;
    private final ObjectMapper objectMapper;
    private final AgentWorkMemoryProperties properties;
    private volatile ObjectMapper persistenceMapper;
    private final ConcurrentMap<String, Integer> lastLedgerSnapshotToolCalls = new ConcurrentHashMap<>();

    public void upsertRun(String agentRunId, AgentChatRequest request, String model) {
        if (!enabled(agentRunId, request)) {
            return;
        }
        safe("upsertAgentRun", agentRunId, () -> backendToolGrpcClient.upsertAgentRun(
                UpsertAgentRunRequest.newBuilder()
                        .setAgentRunId(agentRunId)
                        .setUserId(request.getUserId())
                        .setSessionId(request.getSessionId())
                        .setProjectId(request.getProjectId())
                        .setGoal(Objects.toString(request.getMessage(), ""))
                        .setStatus("running")
                        .setModel(Objects.toString(model, ""))
                        .setStartedAt(nowText())
                        .setMetadataJson(runMetadata(request))
                        .build()));
    }

    public void finishRun(String agentRunId,
                          AgentChatRequest request,
                          String status,
                          String errorMessage,
                          AgentRunLedger.Snapshot snapshot) {
        String runId = Objects.toString(agentRunId, "").trim();
        if (!runId.isBlank()) {
            lastLedgerSnapshotToolCalls.remove(runId);
        }
        if (!enabled(agentRunId, request)) {
            return;
        }
        AgentRunLedger.BudgetSnapshot budget = snapshot == null ? null : snapshot.budget();
        safe("finishAgentRun", agentRunId, () -> backendToolGrpcClient.finishAgentRun(
                FinishAgentRunRequest.newBuilder()
                        .setAgentRunId(agentRunId)
                        .setUserId(request.getUserId())
                        .setSessionId(request.getSessionId())
                        .setProjectId(request.getProjectId())
                        .setStatus(Objects.toString(status, "completed"))
                        .setErrorMessage(Objects.toString(errorMessage, ""))
                        .setEndedAt(nowText())
                        .setTotalModelCalls(budget == null ? 0 : budget.modelCalls())
                        .setTotalToolCalls(budget == null ? 0 : budget.toolCalls())
                        .setTotalToolResultOpenCalls(budget == null ? 0 : budget.toolResultOpenCalls())
                        .setMetadataJson(snapshotMetadata(snapshot))
                        .build()));
    }

    public void recordModelCall(String agentRunId,
                                AgentChatRequest request,
                                String providerId,
                                String model,
                                String callSite,
                                List<ResponseInputItem> input,
                                List<Tool> tools,
                                String instructions,
                                long maxOutputTokens,
                                Response response,
                                long elapsedMs) {
        if (!enabled(agentRunId, request)) {
            return;
        }
        Optional<ResponseUsage> usage = response == null ? Optional.empty() : response.usage();
        AgentTokenUsage tokenUsage = usage
                .map(u -> AgentTokenUsage.newBuilder()
                        .setInputTokens(safeInt(u.inputTokens()))
                        .setOutputTokens(safeInt(u.outputTokens()))
                        .setTotalTokens(safeInt(u.totalTokens()))
                        .build())
                .orElseGet(() -> AgentTokenUsage.newBuilder().build());
        String rawUsageJson = usage.map(this::usageJson).orElse("");
        safe("recordAgentModelCall", agentRunId, () -> backendToolGrpcClient.recordAgentModelCall(
                RecordAgentModelCallRequest.newBuilder()
                        .setAgentRunId(agentRunId)
                        .setUserId(request.getUserId())
                        .setSessionId(request.getSessionId())
                        .setProjectId(request.getProjectId())
                        .setProvider(Objects.toString(providerId, "openai"))
                        .setCallSite(Objects.toString(callSite, ""))
                        .setModel(Objects.toString(model, ""))
                        .setResponseId(response == null ? "" : Objects.toString(response.id(), ""))
                        .setInputItems(input == null ? 0 : input.size())
                        .setEstimatedInputChars(safeInt(estimateInputChars(input)))
                        .setInstructionsChars(Objects.toString(instructions, "").length())
                        .setToolsCount(tools == null ? 0 : tools.size())
                        .setMaxOutputTokens(safeInt(maxOutputTokens))
                        .setServiceTier(response == null ? "" : response.serviceTier()
                                .map(serviceTier -> serviceTier.asString()).orElse(""))
                        .setAttempt(1)
                        .setSuccess(response != null)
                        .setUsage(tokenUsage)
                        .setElapsedMs(safeInt(elapsedMs))
                        .setRawUsageJson(rawUsageJson)
                        .build()));
    }

    public void recordContextCompaction(String agentRunId,
                                        AgentChatRequest request,
                                        String model,
                                        String instructions,
                                        long compactThreshold,
                                        Response response,
                                        List<ResponseInputItem> compactedInput,
                                        int sourceFirstItemIdx,
                                        int sourceLastItemIdx,
                                        int estimatedInputChars,
                                        int estimatedSavedChars,
                                        String inputFingerprint,
                                        String callSite) {
        if (!enabled(agentRunId, request)) {
            return;
        }
        String compactedInputJson = compactedWindowJson(compactedInput);
        if (compactedInputJson.isBlank()) {
            log.warn("[Agent][Persistence] skip context compaction record: runId={}, responseId={}, reason=empty_window",
                    agentRunId, response == null ? "" : response.id());
            return;
        }
        Optional<ResponseUsage> usage = response == null ? Optional.empty() : response.usage();
        String rawUsageJson = usage.map(this::usageJson).orElse("");
        safe("recordAgentContextCompaction", agentRunId, () -> backendToolGrpcClient.recordAgentContextCompaction(
                RecordAgentContextCompactionRequest.newBuilder()
                        .setAgentRunId(agentRunId)
                        .setUserId(request.getUserId())
                        .setSessionId(request.getSessionId())
                        .setProjectId(request.getProjectId())
                        .setProvider("openai")
                        .setModel(Objects.toString(model, ""))
                        .setMode("auto")
                        .setStatus("active")
                        .setContextFormat("responses_compacted_window_v1")
                        .setSourceFirstItemIdx(Math.max(sourceFirstItemIdx, 0))
                        .setSourceLastItemIdx(Math.max(sourceLastItemIdx, 0))
                        .setInstructionsHash(sha256Hex(instructions))
                        .setInputFingerprint(Objects.toString(inputFingerprint, ""))
                        .setCompactThreshold(safeInt(compactThreshold))
                        .setCompactedInputJson(compactedInputJson)
                        .setCompactedItemCount(compactedInput == null ? 0 : compactedInput.size())
                        .setEstimatedInputChars(safeInt(estimatedInputChars))
                        .setEstimatedSavedChars(safeInt(estimatedSavedChars))
                        .setResponseId(response == null ? "" : Objects.toString(response.id(), ""))
                        .setPreviousResponseId(response == null ? "" : response.previousResponseId().orElse(""))
                        .setRawUsageJson(rawUsageJson)
                        .setMetadataJson(contextCompactionMetadata(callSite))
                        .build()));
    }

    public void recordToolCall(ToolExecutionContext context,
                               String callId,
                               String toolName,
                               String argumentsJson,
                               String resultJson,
                               int resultChars,
                               String resultSummary,
                               String status,
                               String errorCode,
                               String readKey,
                               String duplicateOfCallId,
                               long costMs,
                               Instant startedAt,
                               Instant finishedAt,
                               boolean compactedForModel) {
        if (!enabled(context)) {
            return;
        }
        String persistedResult = cappedResultJson(resultJson);
        safe("recordAgentToolCall", context.agentRunId(), () -> backendToolGrpcClient.recordAgentToolCall(
                RecordAgentToolCallRequest.newBuilder()
                        .setAgentRunId(context.agentRunId())
                        .setCallId(Objects.toString(callId, ""))
                        .setUserId(context.userId())
                        .setSessionId(context.sessionId())
                        .setProjectId(context.projectId())
                        .setToolName(Objects.toString(toolName, ""))
                        .setArgumentsJson(Objects.toString(argumentsJson, ""))
                        .setStatus(Objects.toString(status, "unknown"))
                        .setSuccess("success".equals(status))
                        .setResultSummary(Objects.toString(resultSummary, ""))
                        .setErrorCode(Objects.toString(errorCode, ""))
                        .setResultChars(Math.max(resultChars, 0))
                        .setReadKey(Objects.toString(readKey, ""))
                        .setDuplicateOfCallId(Objects.toString(duplicateOfCallId, ""))
                        .setCostMs(safeInt(costMs))
                        .setStartedAt(timeText(startedAt))
                        .setFinishedAt(timeText(finishedAt))
                        .setResultJson(persistedResult)
                        .setCompactedForModel(compactedForModel)
                        .build()));
    }

    public void upsertLedgerSnapshotIfDue(ToolExecutionContext context, AgentRunLedger.Snapshot snapshot) {
        if (!enabled(context) || snapshot == null) {
            return;
        }
        AgentRunLedger.BudgetSnapshot budget = snapshot.budget();
        int toolCalls = budget == null ? 0 : Math.max(budget.toolCalls(), 0);
        int interval = Math.max(properties.getLedgerSnapshotFlushToolCallInterval(), 0);
        if (interval <= 1 || toolCalls <= 0) {
            upsertLedgerSnapshot(context, snapshot);
            return;
        }

        String runId = Objects.toString(context.agentRunId(), "").trim();
        int lastFlushedToolCalls = lastLedgerSnapshotToolCalls.getOrDefault(runId, 0);
        if (toolCalls - lastFlushedToolCalls < interval) {
            log.debug("[Agent][Persistence] skip ledger snapshot flush: runId={}, toolCalls={}, lastFlushed={}, interval={}",
                    runId, toolCalls, lastFlushedToolCalls, interval);
            return;
        }
        upsertLedgerSnapshot(context, snapshot);
    }

    public void upsertLedgerSnapshot(ToolExecutionContext context, AgentRunLedger.Snapshot snapshot) {
        if (!enabled(context) || snapshot == null) {
            return;
        }
        String ledgerJson;
        try {
            ledgerJson = persistenceMapper().writeValueAsString(snapshot);
        } catch (Exception e) {
            log.warn("[Agent][Persistence] serialize ledger snapshot failed: runId={}, error={}",
                    context.agentRunId(), e.getMessage());
            return;
        }
        AgentRunLedger.BudgetSnapshot budget = snapshot.budget();
        safe("upsertAgentLedgerSnapshot", context.agentRunId(), () -> backendToolGrpcClient.upsertAgentLedgerSnapshot(
                UpsertAgentLedgerSnapshotRequest.newBuilder()
                        .setAgentRunId(context.agentRunId())
                        .setUserId(context.userId())
                        .setSessionId(context.sessionId())
                        .setProjectId(context.projectId())
                        .setLedgerJson(ledgerJson)
                        .setModelCalls(budget == null ? 0 : budget.modelCalls())
                        .setToolCalls(budget == null ? 0 : budget.toolCalls())
                        .setToolResultOpenCalls(budget == null ? 0 : budget.toolResultOpenCalls())
                        .build()));
        markLedgerSnapshotFlushed(context.agentRunId(), budget);
    }

    private void markLedgerSnapshotFlushed(String agentRunId, AgentRunLedger.BudgetSnapshot budget) {
        String runId = Objects.toString(agentRunId, "").trim();
        if (runId.isBlank() || budget == null) {
            return;
        }
        lastLedgerSnapshotToolCalls.put(runId, Math.max(budget.toolCalls(), 0));
    }

    private ObjectMapper persistenceMapper() {
        ObjectMapper mapper = persistenceMapper;
        if (mapper != null) {
            return mapper;
        }
        synchronized (this) {
            mapper = persistenceMapper;
            if (mapper == null) {
                mapper = objectMapper.copy()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                persistenceMapper = mapper;
            }
            return mapper;
        }
    }

    private boolean enabled(String agentRunId, AgentChatRequest request) {
        return properties.isPersistenceEnabled()
                && request != null
                && request.getUserId() > 0
                && !Objects.toString(agentRunId, "").trim().isBlank();
    }

    private boolean enabled(ToolExecutionContext context) {
        return properties.isPersistenceEnabled()
                && context != null
                && context.userId() > 0
                && !Objects.toString(context.agentRunId(), "").trim().isBlank();
    }

    private void safe(String action, String agentRunId, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("[Agent][Persistence] {} failed: runId={}, error={}",
                    action, agentRunId, e.getMessage());
        }
    }

    private String cappedResultJson(String raw) {
        String value = Objects.toString(raw, "");
        int max = Math.max(properties.getMaxPersistedToolResultChars(), 0);
        if (max <= 0 || value.isBlank() || value.length() > max) {
            return "";
        }
        return value;
    }

    private String runMetadata(AgentChatRequest request) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("locale", request.getLocale());
        node.put("enable_tools", request.getEnableTools());
        node.put("enable_image", request.getEnableImage());
        node.put("enable_priority_processing", request.getEnablePriorityProcessing());
        node.put("work_memory_enabled", properties.isEnabled());
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String snapshotMetadata(AgentRunLedger.Snapshot snapshot) {
        if (snapshot == null) {
            return "{}";
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("goal", snapshot.goal());
        node.put("status", snapshot.status());
        node.put("created_at", timeText(snapshot.createdAt()));
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String usageJson(ResponseUsage usage) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("input_tokens", usage.inputTokens());
        node.put("output_tokens", usage.outputTokens());
        node.put("total_tokens", usage.totalTokens());
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String compactedWindowJson(List<ResponseInputItem> compactedInput) {
        if (compactedInput == null || compactedInput.isEmpty()) {
            return "";
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("format", "responses_compacted_window_v1");
        root.put("schema_version", 1);
        root.put("provider", "openai");
        ArrayNode items = root.putArray("items");
        for (ResponseInputItem item : compactedInput) {
            JsonNode node = responseInputItemJson(item);
            if (node != null && !node.isNull() && !node.isMissingNode()) {
                items.add(node);
            }
        }
        if (items.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("[Agent][Persistence] serialize compacted window failed: {}", e.getMessage());
            return "";
        }
    }

    private JsonNode responseInputItemJson(ResponseInputItem item) {
        if (item == null) {
            return null;
        }
        if (item.isCompaction()) {
            return compactionItemJson(item.asCompaction());
        }
        try {
            JsonNode converted = objectMapper.valueToTree(item);
            if (converted != null && !converted.isMissingNode() && !converted.isNull()) {
                return converted;
            }
        } catch (Exception ignore) {
        }
        ObjectNode fallback = objectMapper.createObjectNode();
        fallback.put("type", responseInputItemKind(item));
        fallback.put("sdk_repr", Objects.toString(item, ""));
        return fallback;
    }

    private JsonNode compactionItemJson(ResponseCompactionItemParam item) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "compaction");
        item.id().ifPresent(id -> node.put("id", id));
        node.put("encrypted_content", item.encryptedContent());
        return node;
    }

    private String contextCompactionMetadata(String callSite) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("capture_source", "responses_output_compaction");
        node.put("call_site", Objects.toString(callSite, ""));
        node.put("complete_window", true);
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String responseInputItemKind(ResponseInputItem item) {
        if (item.isEasyInputMessage()) return "easy_input_message";
        if (item.isMessage()) return "message";
        if (item.isResponseOutputMessage()) return "response_output_message";
        if (item.isReasoning()) return "reasoning";
        if (item.isFunctionCall()) return "function_call";
        if (item.isFunctionCallOutput()) return "function_call_output";
        if (item.isWebSearchCall()) return "web_search_call";
        if (item.isCompaction()) return "compaction";
        return "unknown";
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

    private static long estimateInputChars(List<ResponseInputItem> input) {
        if (input == null || input.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (ResponseInputItem item : input) {
            total += Objects.toString(item, "").length();
        }
        return total;
    }

    private static int safeInt(long value) {
        if (value <= 0) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static String nowText() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }

    private static String timeText(Instant instant) {
        return instant == null ? "" : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC).toString();
    }
}
