package com.xiaoyuan.paperpuppybackendintl.grpc.server.service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.JsonStructureReadRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.JsonSubtreeReadRequest;
import com.xiaoyuan.paperpuppybackendintl.service.json.JsonResourceService;
import com.xiaoyuan.paperpuppybackendintl.service.json.JsonStructureService;
import com.xiaoyuan.paperpuppybackendintl.service.json.JsonSubtreeService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolJsonStructureHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void jsonStructureReadBuildsPayloadWithStructureMetadata() throws Exception {
        JsonStructureService jsonStructureService = mock(JsonStructureService.class);
        JsonSubtreeService jsonSubtreeService = mock(JsonSubtreeService.class);
        AgentToolJsonStructureHandler handler = new AgentToolJsonStructureHandler(
                objectMapper,
                jsonStructureService,
                jsonSubtreeService
        );

        ObjectNode stats = objectMapper.createObjectNode();
        stats.put("approx_depth", 4);
        stats.put("object_count", 3);
        ObjectNode skeleton = objectMapper.createObjectNode();
        skeleton.put("project", "object");

        when(jsonStructureService.readStructure(eq(7L), eq(13L), eq(55L), eq(0L), eq("/"), eq(4), eq(3), eq(80)))
                .thenReturn(new JsonStructureService.JsonStructureResult(
                        new JsonResourceService.JsonResourceSnapshot(
                                "file",
                                55L,
                                0L,
                                false,
                                "rev-structure",
                                "demo.json",
                                "",
                                objectMapper.createObjectNode()
                        ),
                        "/",
                        "object",
                        false,
                        stats,
                        skeleton,
                        List.of("/project", "/cards")
                ));

        var response = handler.jsonStructureRead(JsonStructureReadRequest.newBuilder()
                .setUserId(7L)
                .setProjectId(13L)
                .setFileId(55L)
                .setPath("/")
                .setMaxDepth(4)
                .setSampleArrayItems(3)
                .setMaxKeysPerObject(80)
                .build());

        assertTrue(response.getSuccess());
        assertEquals("file", response.getResourceKind());
        assertEquals(55L, response.getFileId());
        assertEquals("rev-structure", response.getRevision());

        var payload = objectMapper.readTree(response.getResultJson());
        assertEquals("demo.json", payload.path("sourceName").asText());
        assertEquals("object", payload.path("nodeType").asText());
        assertEquals(4, payload.path("stats").path("approx_depth").asInt());
        assertEquals("object", payload.path("skeleton").path("project").asText());
        assertEquals("/project", payload.path("children").get(0).asText());
        assertEquals("/cards", payload.path("children").get(1).asText());
    }

    @Test
    void jsonSubtreeReadBuildsPayloadWithBoundedValueMetadata() throws Exception {
        JsonStructureService jsonStructureService = mock(JsonStructureService.class);
        JsonSubtreeService jsonSubtreeService = mock(JsonSubtreeService.class);
        AgentToolJsonStructureHandler handler = new AgentToolJsonStructureHandler(
                objectMapper,
                jsonStructureService,
                jsonSubtreeService
        );

        when(jsonSubtreeService.readSubtree(eq(7L), eq(13L), eq(55L), eq(0L), eq("/cards/0"), eq(2048), eq(100)))
                .thenReturn(new JsonSubtreeService.JsonSubtreeResult(
                        new JsonResourceService.JsonResourceSnapshot(
                                "file",
                                55L,
                                0L,
                                false,
                                "rev-subtree",
                                "demo.json",
                                "",
                                objectMapper.createObjectNode()
                        ),
                        "/cards/0",
                        "object",
                        true,
                        3,
                        128,
                        objectMapper.readTree("""
                                {
                                  "title": "Intro",
                                  "body": "Hello"
                                }
                                """)
                ));

        var response = handler.jsonSubtreeRead(JsonSubtreeReadRequest.newBuilder()
                .setUserId(7L)
                .setProjectId(13L)
                .setFileId(55L)
                .setPath("/cards/0")
                .setMaxBytes(2048)
                .setMaxNodes(100)
                .build());

        assertTrue(response.getSuccess());
        assertEquals("file", response.getResourceKind());
        assertEquals(55L, response.getFileId());
        assertEquals("rev-subtree", response.getRevision());

        var payload = objectMapper.readTree(response.getResultJson());
        assertEquals("/cards/0", payload.path("path").asText());
        assertEquals("object", payload.path("nodeType").asText());
        assertTrue(payload.path("truncated").asBoolean());
        assertEquals(3, payload.path("returnedNodeCount").asInt());
        assertEquals(128, payload.path("approxBytes").asInt());
        assertEquals("Intro", payload.path("value").path("title").asText());
    }

}
