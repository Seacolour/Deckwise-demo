package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.grpc.backend.JsonStructureReadResponse;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonStructureReadToolTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void executeReturnsStructureEnvelopeForJsonFile() throws Exception {
        BackendToolGrpcClient backendClient = mock(BackendToolGrpcClient.class);
        JsonStructureReadTool tool = new JsonStructureReadTool(om, backendClient);

        when(backendClient.jsonStructureRead(eq(7L), eq(13L), eq(55L), eq(0L), eq("/cards"), eq(4), eq(3), eq(80)))
                .thenReturn(JsonStructureReadResponse.newBuilder()
                        .setSuccess(true)
                        .setResourceKind("file")
                        .setFileId(55L)
                        .setRevision("rev-123")
                        .setResultJson("""
                                {
                                  "resourceKind": "file",
                                  "fileId": 55,
                                  "writable": false,
                                  "revision": "rev-123",
                                  "sourceName": "deck.json",
                                  "path": "/cards",
                                  "nodeType": "array",
                                  "truncated": false,
                                  "stats": {
                                    "approx_depth": 4
                                  },
                                  "skeleton": {
                                    "$type": "array<object>"
                                  },
                                  "children": [
                                    "/cards/0",
                                    "/cards/1"
                                  ]
                                }
                                """)
                        .build());

        ObjectNode arguments = om.createObjectNode();
        arguments.put("file_id", 55L);
        arguments.put("path", "/cards");

        JsonNode result = tool.execute(arguments, new ToolExecutionContext(7L, 13L, 21L));

        assertTrue(result.path("success").asBoolean(false));
        assertEquals(55L, result.path("file_id").asLong());
        assertEquals("file", result.path("resource_kind").asText());
        assertEquals("deck.json", result.path("source_name").asText());
        assertEquals("/cards", result.path("path").asText());
        assertEquals("array", result.path("node_type").asText());
        assertEquals("array<object>", result.path("skeleton").path("$type").asText());
        assertEquals("/cards/0", result.path("children").get(0).asText());

        verify(backendClient).jsonStructureRead(eq(7L), eq(13L), eq(55L), eq(0L), eq("/cards"), eq(4), eq(3), eq(80));
    }
}
