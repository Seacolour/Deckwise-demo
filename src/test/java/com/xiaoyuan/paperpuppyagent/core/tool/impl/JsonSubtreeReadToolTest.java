package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.grpc.backend.JsonSubtreeReadResponse;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonSubtreeReadToolTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void executeReturnsSubtreeEnvelopeForJsonFile() throws Exception {
        BackendToolGrpcClient backendClient = mock(BackendToolGrpcClient.class);
        JsonSubtreeReadTool tool = new JsonSubtreeReadTool(om, backendClient);

        when(backendClient.jsonSubtreeRead(eq(7L), eq(13L), eq(55L), eq(0L), eq("/cards/0"), eq(2048), eq(100)))
                .thenReturn(JsonSubtreeReadResponse.newBuilder()
                        .setSuccess(true)
                        .setResourceKind("file")
                        .setFileId(55L)
                        .setRevision("rev-234")
                        .setResultJson("""
                                {
                                  "resourceKind": "file",
                                  "fileId": 55,
                                  "writable": false,
                                  "revision": "rev-234",
                                  "sourceName": "deck.json",
                                  "path": "/cards/0",
                                  "nodeType": "object",
                                  "truncated": true,
                                  "returnedNodeCount": 3,
                                  "approxBytes": 120,
                                  "value": {
                                    "title": "Intro",
                                    "body": "Hello"
                                  }
                                }
                                """)
                        .build());

        ObjectNode arguments = om.createObjectNode();
        arguments.put("file_id", 55L);
        arguments.put("path", "/cards/0");
        arguments.put("max_bytes", 2048);
        arguments.put("max_nodes", 100);

        JsonNode result = tool.execute(arguments, new ToolExecutionContext(7L, 13L, 21L));

        assertTrue(result.path("success").asBoolean(false));
        assertEquals(55L, result.path("file_id").asLong());
        assertEquals("file", result.path("resource_kind").asText());
        assertEquals("/cards/0", result.path("path").asText());
        assertEquals("object", result.path("node_type").asText());
        assertTrue(result.path("truncated").asBoolean());
        assertEquals(3, result.path("returned_node_count").asInt());
        assertEquals(120, result.path("approx_bytes").asInt());
        assertEquals("Intro", result.path("value").path("title").asText());

        verify(backendClient).jsonSubtreeRead(eq(7L), eq(13L), eq(55L), eq(0L), eq("/cards/0"), eq(2048), eq(100));
    }
}
