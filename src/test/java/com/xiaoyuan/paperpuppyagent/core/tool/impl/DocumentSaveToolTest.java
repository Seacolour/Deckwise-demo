package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.core.tool.document.DocumentEditSessionManager;
import com.xiaoyuan.paperpuppyagent.grpc.backend.UpdateDocumentDraftResponse;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentSaveToolTest {

    @Test
    void executeUsesPersistedDocumentVersionAsExpectedVersion() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BackendToolGrpcClient backendClient = mock(BackendToolGrpcClient.class);
        DocumentEditSessionManager sessionManager = new DocumentEditSessionManager(128, 30);
        sessionManager.createOrUpdate(30L, 40L, "draft body", 7);

        when(backendClient.updateDocumentDraft(eq(10L), eq(20L), eq(40L), eq(7), eq("draft body"), eq("save now")))
                .thenReturn(UpdateDocumentDraftResponse.newBuilder()
                        .setSuccess(true)
                        .setDocumentId(40L)
                        .setNewVersion(8)
                        .build());

        DocumentSaveTool tool = new DocumentSaveTool(mapper, backendClient, sessionManager);
        ObjectNode arguments = mapper.createObjectNode();
        arguments.put("document_id", 40L);
        arguments.put("message", "save now");

        var result = tool.execute(arguments, new ToolExecutionContext(10L, 20L, 30L));

        verify(backendClient).updateDocumentDraft(10L, 20L, 40L, 7, "draft body", "save now");
        assertEquals(true, result.get("success").asBoolean());
        assertEquals(40L, result.get("document_id").asLong());
        assertEquals("7", result.get("before_revision").asText());
        assertEquals("8", result.get("revision").asText());
        assertEquals(8, result.get("saved_version").asInt());
    }
}
