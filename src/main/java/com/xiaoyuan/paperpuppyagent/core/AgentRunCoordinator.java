package com.xiaoyuan.paperpuppyagent.core;

import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunLedger;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunLedgerStore;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunPersistenceReporter;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentToolResultStore;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.core.tool.document.DocumentEditSessionManager;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Owns run creation, status, persistence finalization, and per-run cleanup. */
@Component
@RequiredArgsConstructor
public class AgentRunCoordinator {
    private final DocumentEditSessionManager documentEditSessionManager;
    private final AgentToolResultStore toolResultStore;
    private final AgentRunLedgerStore ledgerStore;
    private final AgentRunPersistenceReporter persistenceReporter;

    public RunScope open(AgentChatRequest request) {
        long sessionId = request == null ? 0L : request.getSessionId();
        return new RunScope(newAgentRunId(), sessionId);
    }

    public void start(RunScope scope, AgentChatRequest request, String model) {
        ledgerStore.createRun(scope.agentRunId(), request.getMessage());
        persistenceReporter.upsertRun(scope.agentRunId(), request, model);
    }

    public void checkpoint(ToolExecutionContext toolContext) {
        if (toolContext == null) {
            return;
        }
        ledgerStore.snapshot(toolContext.agentRunId())
                .ifPresent(snapshot -> persistenceReporter.upsertLedgerSnapshotIfDue(toolContext, snapshot));
    }

    public void finish(RunScope scope, AgentChatRequest request) {
        ToolExecutionContext snapshotContext = new ToolExecutionContext(
                request == null ? 0L : request.getUserId(),
                request == null ? 0L : request.getProjectId(),
                request == null ? 0L : request.getSessionId(),
                scope.agentRunId());
        Optional<AgentRunLedger.Snapshot> finalSnapshot = ledgerStore.snapshot(scope.agentRunId());
        finalSnapshot.ifPresent(snapshot -> {
            persistenceReporter.upsertLedgerSnapshot(snapshotContext, snapshot);
            persistenceReporter.finishRun(
                    scope.agentRunId(), request, scope.status(), scope.error(), snapshot);
        });
        if (finalSnapshot.isEmpty()) {
            persistenceReporter.finishRun(
                    scope.agentRunId(), request, scope.status(), scope.error(), null);
        }
        if (scope.sessionIdForCleanup() > 0) {
            try {
                documentEditSessionManager.clearSession(scope.sessionIdForCleanup());
            } catch (Exception ignore) {
                // Cleanup remains best effort and must not mask the run outcome.
            }
        }
        toolResultStore.clearRun(scope.agentRunId());
        ledgerStore.clearRun(scope.agentRunId());
    }

    private static String newAgentRunId() {
        return "run_" + UUID.randomUUID().toString().replace("-", "");
    }

    public static final class RunScope {
        private final String agentRunId;
        private final long sessionIdForCleanup;
        private final AtomicReference<String> status = new AtomicReference<>("running");
        private final AtomicReference<String> error = new AtomicReference<>("");

        private RunScope(String agentRunId, long sessionIdForCleanup) {
            this.agentRunId = agentRunId;
            this.sessionIdForCleanup = sessionIdForCleanup;
        }

        public String agentRunId() {
            return agentRunId;
        }

        public long sessionIdForCleanup() {
            return sessionIdForCleanup;
        }

        public String status() {
            return status.get();
        }

        public String error() {
            return error.get();
        }

        public void mark(String nextStatus) {
            status.set(nextStatus);
        }

        public void fail(Throwable throwable) {
            status.set("failed");
            error.set(throwable == null || throwable.getMessage() == null ? "" : throwable.getMessage());
        }
    }
}
