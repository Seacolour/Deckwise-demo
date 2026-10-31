package com.xiaoyuan.paperpuppyagent.core.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppyagent.config.AgentWorkMemoryProperties;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatRequest;
import com.xiaoyuan.paperpuppyagent.grpc.backend.RecordAgentModelCallRequest;
import com.xiaoyuan.paperpuppyagent.grpc.backend.UpsertAgentLedgerSnapshotRequest;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentRunPersistenceReporterTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void upsertLedgerSnapshotSerializesJavaTimeFields() throws Exception {
        AgentWorkMemoryProperties properties = new AgentWorkMemoryProperties();
        properties.setEnabled(true);
        properties.setPersistenceEnabled(true);

        AgentRunLedgerStore ledgerStore = new AgentRunLedgerStore(om, properties);
        ledgerStore.createRun("run_serializable", "translate deck");
        ledgerStore.addTodo("run_serializable", "todo_1", "finish page 10", "done", "", "");
        AgentRunLedger.Snapshot snapshot = ledgerStore.snapshot("run_serializable").orElseThrow();

        BackendToolGrpcClient backendToolGrpcClient = mock(BackendToolGrpcClient.class);
        AgentRunPersistenceReporter reporter = new AgentRunPersistenceReporter(
                backendToolGrpcClient,
                om,
                properties);

        reporter.upsertLedgerSnapshot(
                new ToolExecutionContext(26, 135, 222, "run_serializable"),
                snapshot);

        ArgumentCaptor<UpsertAgentLedgerSnapshotRequest> captor =
                ArgumentCaptor.forClass(UpsertAgentLedgerSnapshotRequest.class);
        verify(backendToolGrpcClient).upsertAgentLedgerSnapshot(captor.capture());

        UpsertAgentLedgerSnapshotRequest request = captor.getValue();
        JsonNode ledgerJson = om.readTree(request.getLedgerJson());
        assertEquals("run_serializable", request.getAgentRunId());
        assertEquals("run_serializable", ledgerJson.path("agentRunId").asText());
        assertTrue(ledgerJson.path("createdAt").isTextual());
        assertTrue(ledgerJson.path("todos").get(0).path("updatedAt").isTextual());
    }

    @Test
    void modelCallPreservesSelectedProviderWithoutPersistingByokKey() {
        AgentWorkMemoryProperties properties = new AgentWorkMemoryProperties();
        properties.setEnabled(true);
        properties.setPersistenceEnabled(true);
        BackendToolGrpcClient backendToolGrpcClient = mock(BackendToolGrpcClient.class);
        AgentRunPersistenceReporter reporter = new AgentRunPersistenceReporter(
                backendToolGrpcClient, om, properties);
        AgentChatRequest chatRequest = AgentChatRequest.newBuilder()
                .setUserId(26).setSessionId(222).setProjectId(135)
                .setProviderId("deepseek").setProviderApiKey("user-secret")
                .build();

        reporter.recordModelCall("run_deepseek", chatRequest, "deepseek", "deepseek-flash",
                "ask", java.util.List.of(), java.util.List.of(), "instructions", 512, null, 10);

        ArgumentCaptor<RecordAgentModelCallRequest> captor =
                ArgumentCaptor.forClass(RecordAgentModelCallRequest.class);
        verify(backendToolGrpcClient).recordAgentModelCall(captor.capture());
        assertEquals("deepseek", captor.getValue().getProvider());
        assertEquals("deepseek-flash", captor.getValue().getModel());
        assertTrue(!captor.getValue().toString().contains("user-secret"));
    }
}
