package com.xiaoyuan.paperpuppybackendintl.grpc.server.service.handler;

import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.FinishAgentRunRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.OpenAgentArtifactViewRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.OpenAgentArtifactViewResponse;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.RecordAgentContextCompactionRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.RecordAgentModelCallRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.RecordAgentToolCallRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.UpsertAgentLedgerSnapshotRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.UpsertAgentRunRequest;
import com.xiaoyuan.paperpuppybackendintl.repository.AgentRunPersistenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Agent run 持久化上报处理。
 *
 * <p>这是阶段三 A 的 backend-owned 事实入口；Agent 侧调用失败时可以忽略，不影响工具主链路。</p>
 */
@Service
@RequiredArgsConstructor
public class AgentToolAgentRunHandler {

    private final AgentRunPersistenceRepository repository;

    public void upsertRun(UpsertAgentRunRequest request) {
        String runId = requireRunId(request.getAgentRunId());
        long userId = requireUserId(request.getUserId());
        repository.upsertRun(AgentRunPersistenceRepository.RunEntry.builder()
                .runId(runId)
                .userId(userId)
                .sessionId(request.getSessionId())
                .projectId(request.getProjectId())
                .goal(request.getGoal())
                .status(request.getStatus())
                .model(request.getModel())
                .startedAt(parseTime(request.getStartedAt()))
                .metadataJson(request.getMetadataJson())
                .build());
    }

    public void finishRun(FinishAgentRunRequest request) {
        String runId = requireRunId(request.getAgentRunId());
        repository.finishRun(AgentRunPersistenceRepository.FinishRunEntry.builder()
                .runId(runId)
                .status(request.getStatus())
                .errorMessage(request.getErrorMessage())
                .endedAt(parseTime(request.getEndedAt()))
                .totalModelCalls(request.getTotalModelCalls())
                .totalToolCalls(request.getTotalToolCalls())
                .totalToolResultOpenCalls(request.getTotalToolResultOpenCalls())
                .inputTokens(request.hasUsage() ? request.getUsage().getInputTokens() : null)
                .outputTokens(request.hasUsage() ? request.getUsage().getOutputTokens() : null)
                .totalTokens(request.hasUsage() ? request.getUsage().getTotalTokens() : null)
                .metadataJson(request.getMetadataJson())
                .build());
    }

    public void recordModelCall(RecordAgentModelCallRequest request) {
        String runId = requireRunId(request.getAgentRunId());
        long userId = requireUserId(request.getUserId());
        repository.recordModelCall(AgentRunPersistenceRepository.ModelCallEntry.builder()
                .runId(runId)
                .userId(userId)
                .sessionId(request.getSessionId())
                .projectId(request.getProjectId())
                .provider(request.getProvider())
                .callSite(request.getCallSite())
                .model(request.getModel())
                .responseId(request.getResponseId())
                .inputItems(request.getInputItems())
                .estimatedInputChars(request.getEstimatedInputChars())
                .instructionsChars(request.getInstructionsChars())
                .toolsCount(request.getToolsCount())
                .maxOutputTokens(request.getMaxOutputTokens())
                .serviceTier(request.getServiceTier())
                .attempt(request.getAttempt())
                .success(request.getSuccess())
                .inputTokens(request.hasUsage() ? request.getUsage().getInputTokens() : null)
                .outputTokens(request.hasUsage() ? request.getUsage().getOutputTokens() : null)
                .totalTokens(request.hasUsage() ? request.getUsage().getTotalTokens() : null)
                .elapsedMs(request.getElapsedMs())
                .errorType(request.getErrorType())
                .errorMessage(request.getErrorMessage())
                .rawUsageJson(request.getRawUsageJson())
                .metadataJson(request.getMetadataJson())
                .build());
    }

    public void recordContextCompaction(RecordAgentContextCompactionRequest request) {
        String runId = requireRunId(request.getAgentRunId());
        long userId = requireUserId(request.getUserId());
        if (request.getSessionId() <= 0) {
            throw new IllegalArgumentException("session_id is required");
        }
        repository.recordContextCompaction(AgentRunPersistenceRepository.ContextCompactionEntry.builder()
                .runId(runId)
                .userId(userId)
                .sessionId(request.getSessionId())
                .projectId(request.getProjectId())
                .provider(request.getProvider())
                .model(request.getModel())
                .mode(request.getMode())
                .status(request.getStatus())
                .contextFormat(request.getContextFormat())
                .sourceFirstSeq(request.getSourceFirstSeq())
                .sourceLastSeq(request.getSourceLastSeq())
                .sourceFirstItemIdx(request.getSourceFirstItemIdx())
                .sourceLastItemIdx(request.getSourceLastItemIdx())
                .instructionsHash(request.getInstructionsHash())
                .inputFingerprint(request.getInputFingerprint())
                .compactThreshold(request.getCompactThreshold())
                .compactedInputJson(request.getCompactedInputJson())
                .compactedItemCount(request.getCompactedItemCount())
                .estimatedInputChars(request.getEstimatedInputChars())
                .estimatedSavedChars(request.getEstimatedSavedChars())
                .responseId(request.getResponseId())
                .previousResponseId(request.getPreviousResponseId())
                .rawUsageJson(request.getRawUsageJson())
                .metadataJson(request.getMetadataJson())
                .errorType(request.getErrorType())
                .errorMessage(request.getErrorMessage())
                .build());
    }

    public void recordToolCall(RecordAgentToolCallRequest request) {
        String runId = requireRunId(request.getAgentRunId());
        long userId = requireUserId(request.getUserId());
        String callId = Objects.toString(request.getCallId(), "").trim();
        if (callId.isBlank()) {
            throw new IllegalArgumentException("agent tool call_id is required");
        }
        repository.recordToolCall(AgentRunPersistenceRepository.ToolCallEntry.builder()
                .runId(runId)
                .callId(callId)
                .userId(userId)
                .sessionId(request.getSessionId())
                .projectId(request.getProjectId())
                .toolName(request.getToolName())
                .argumentsJson(request.getArgumentsJson())
                .status(request.getStatus())
                .success(request.getSuccess())
                .resultSummary(request.getResultSummary())
                .errorCode(request.getErrorCode())
                .resultChars(request.getResultChars())
                .readKey(request.getReadKey())
                .duplicateOfCallId(request.getDuplicateOfCallId())
                .costMs(request.getCostMs())
                .startedAt(parseTime(request.getStartedAt()))
                .finishedAt(parseTime(request.getFinishedAt()))
                .resultJson(request.getResultJson())
                .compactedForModel(request.getCompactedForModel())
                .build());
    }

    public void upsertLedgerSnapshot(UpsertAgentLedgerSnapshotRequest request) {
        String runId = requireRunId(request.getAgentRunId());
        long userId = requireUserId(request.getUserId());
        repository.upsertLedgerSnapshot(AgentRunPersistenceRepository.LedgerSnapshotEntry.builder()
                .runId(runId)
                .userId(userId)
                .sessionId(request.getSessionId())
                .projectId(request.getProjectId())
                .ledgerJson(request.getLedgerJson())
                .modelCalls(request.getModelCalls())
                .toolCalls(request.getToolCalls())
                .toolResultOpenCalls(request.getToolResultOpenCalls())
                .build());
    }

    public OpenAgentArtifactViewResponse openArtifactView(OpenAgentArtifactViewRequest request) {
        long userId = requireUserId(request.getUserId());
        if (request.getSessionId() <= 0) {
            throw new IllegalArgumentException("session_id is required");
        }
        return repository.openArtifactViewJson(
                        userId,
                        request.getSessionId(),
                        request.getProjectId() > 0 ? request.getProjectId() : null,
                        request.getViewKey(),
                        request.getDocumentId() > 0 ? request.getDocumentId() : null,
                        request.getViewType(),
                        request.getMode(),
                        request.getPath(),
                        request.getMaxChars()
                )
                .map(resultJson -> OpenAgentArtifactViewResponse.newBuilder()
                        .setSuccess(true)
                        .setResultJson(resultJson)
                        .build())
                .orElseGet(() -> OpenAgentArtifactViewResponse.newBuilder()
                        .setSuccess(false)
                        .setError("artifact_view_not_found")
                        .build());
    }

    private static String requireRunId(String value) {
        String runId = Objects.toString(value, "").trim();
        if (runId.isBlank()) {
            throw new IllegalArgumentException("agent_run_id is required");
        }
        return runId;
    }

    private static long requireUserId(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("user_id is required");
        }
        return value;
    }

    private static OffsetDateTime parseTime(String value) {
        String text = Objects.toString(value, "").trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (Exception ignore) {
            return null;
        }
    }
}
