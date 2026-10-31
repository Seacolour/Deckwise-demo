package com.xiaoyuan.paperpuppyagent.core;

import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunLedgerStore;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunPersistenceReporter;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reports completed tool facts to backend persistence without owning execution. */
@Component
@RequiredArgsConstructor
public class ToolFactReporter {
    private final AgentRunPersistenceReporter persistenceReporter;

    public void report(ToolExecutionContext toolContext,
                       ToolInvocationExecutor.InvocationResult invocation,
                       ToolResultProcessor.ProcessedToolResult processed) {
        AgentRunLedgerStore.ToolCallObservation observation = processed.ledgerObservation();
        persistenceReporter.recordToolCall(
                toolContext,
                invocation.callId(),
                invocation.name(),
                invocation.argumentsJson(),
                processed.resultJson(),
                processed.resultJson().length(),
                processed.resultSummary(),
                invocation.error() ? "error" : "success",
                invocation.error() ? invocation.errorCode() : "",
                observation == null ? "" : observation.readKey(),
                observation == null ? "" : observation.duplicateOfCallId(),
                invocation.costMs(),
                invocation.startedAt(),
                invocation.finishedAt(),
                processed.compactedForModel());
    }
}
