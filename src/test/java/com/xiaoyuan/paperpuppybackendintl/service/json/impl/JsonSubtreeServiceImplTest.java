package com.xiaoyuan.paperpuppybackendintl.service.json.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.service.json.JsonResourceService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonSubtreeServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readSubtreeReturnsNestedNodeWithoutTruncation() throws Exception {
        JsonResourceService jsonResourceService = mock(JsonResourceService.class);
        JsonSubtreeServiceImpl service = new JsonSubtreeServiceImpl(objectMapper, jsonResourceService);

        JsonNode root = objectMapper.readTree("""
                {
                  "cards": [
                    {
                      "id": "c1",
                      "title": "Intro",
                      "body": {
                        "type": "paragraph",
                        "text": "Hello"
                      }
                    }
                  ]
                }
                """);
        when(jsonResourceService.readResource(eq(7L), eq(13L), eq(55L), eq(0L)))
                .thenReturn(new JsonResourceService.JsonResourceSnapshot(
                        "file",
                        55L,
                        0L,
                        false,
                        "rev-file",
                        "demo.json",
                        "",
                        root
                ));

        var result = service.readSubtree(7L, 13L, 55L, 0L, "/cards/0/body", 2048, 100);

        assertEquals("/cards/0/body", result.path());
        assertEquals("object", result.nodeType());
        assertEquals(3, result.returnedNodeCount());
        assertEquals("paragraph", result.value().path("type").asText());
        assertEquals("Hello", result.value().path("text").asText());
        assertTrue(!result.truncated());
    }

    @Test
    void readSubtreeTruncatesLargeTextAndNodeBudget() throws Exception {
        JsonResourceService jsonResourceService = mock(JsonResourceService.class);
        JsonSubtreeServiceImpl service = new JsonSubtreeServiceImpl(objectMapper, jsonResourceService);

        JsonNode root = objectMapper.readTree("""
                {
                  "cards": [
                    {
                      "title": "Intro",
                      "body": {
                        "text": "This is a very long paragraph that should be truncated aggressively for subtree reads.",
                        "notes": [
                          "a",
                          "b",
                          "c",
                          "d"
                        ]
                      }
                    }
                  ]
                }
                """);
        when(jsonResourceService.readResource(eq(7L), eq(13L), eq(55L), eq(0L)))
                .thenReturn(new JsonResourceService.JsonResourceSnapshot(
                        "file",
                        55L,
                        0L,
                        false,
                        "rev-file",
                        "demo.json",
                        "",
                        root
                ));

        var result = service.readSubtree(7L, 13L, 55L, 0L, "/cards/0/body", 80, 4);

        assertEquals("/cards/0/body", result.path());
        assertEquals("object", result.nodeType());
        assertTrue(result.truncated());
        assertTrue(result.approxBytes() <= 80);
        assertTrue(result.returnedNodeCount() <= 4);
        assertTrue(result.value().path("text").asText().length() < "This is a very long paragraph that should be truncated aggressively for subtree reads.".length());
    }
}
