package com.xiaoyuan.paperpuppyagent.core;

import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunLedgerStore;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunPersistenceReporter;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentToolResultStore;
import com.xiaoyuan.paperpuppyagent.core.tool.document.DocumentEditSessionManager;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatRequest;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunCoordinatorTest {

    @Test
    void finishPersistsStatusAndClearsEveryRunScopedStore() {
        DocumentEditSessionManager editSessionManager = mock(DocumentEditSessionManager.class);
        AgentToolResultStore toolResultStore = mock(AgentToolResultStore.class);
        AgentRunLedgerStore ledgerStore = mock(AgentRunLedgerStore.class);
        AgentRunPersistenceReporter persistenceReporter = mock(AgentRunPersistenceReporter.class);
        AgentRunCoordinator coordinator = new AgentRunCoordinator(
                editSessionManager, toolResultStore, ledgerStore, persistenceReporter);
        AgentChatRequest request = AgentChatRequest.newBuilder()
                .setUserId(7L)
                .setProjectId(8L)
                .setSessionId(9L)
                .setMessage("hello")
                .build();

        AgentRunCoordinator.RunScope scope = coordinator.open(request);
        assertTrue(scope.agentRunId().startsWith("run_"));
        coordinator.start(scope, request, "gpt-test");
        scope.mark("completed");
        when(ledgerStore.snapshot(scope.agentRunId())).thenReturn(Optional.empty());

        coordinator.finish(scope, request);

        verify(ledgerStore).createRun(scope.agentRunId(), "hello");
        verify(persistenceReporter).upsertRun(scope.agentRunId(), request, "gpt-test");
        verify(persistenceReporter).finishRun(
                eq(scope.agentRunId()), eq(request), eq("completed"), eq(""), isNull());
        verify(editSessionManager).clearSession(9L);
        verify(toolResultStore).clearRun(scope.agentRunId());
        verify(ledgerStore).clearRun(scope.agentRunId());
    }
}
