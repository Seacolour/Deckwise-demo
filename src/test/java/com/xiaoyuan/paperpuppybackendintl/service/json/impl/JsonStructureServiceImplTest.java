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

class JsonStructureServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readStructureTruncatesWideObjectsAtConfiguredKeyLimit() throws Exception {
        JsonResourceService jsonResourceService = mock(JsonResourceService.class);
        JsonStructureServiceImpl service = new JsonStructureServiceImpl(objectMapper, jsonResourceService);

        JsonNode root = objectMapper.readTree("""
                {
                  "project": {
                    "title": "Deck",
                    "meta": {
                      "theme": "light"
                    }
                  },
                  "cards": [
                    {
                      "id": "c1",
                      "title": "Intro"
                    }
                  ],
                  "flags": [
                    true,
                    false
                  ],
                  "metadata": {
                    "owner": "demo"
                  }
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

        var result = service.readStructure(7L, 13L, 55L, 0L, "/", 3, 2, 3);

        assertEquals("/", result.path());
        assertEquals("object", result.nodeType());
        assertTrue(result.truncated());
        assertEquals(3, result.children().size());
        assertEquals("/project", result.children().get(0));
        assertEquals("/cards", result.children().get(1));
        assertEquals("/flags", result.children().get(2));
        assertEquals(5, result.stats().path("object_count").asInt());
        assertEquals(2, result.stats().path("array_count").asInt());
        assertEquals("string", result.skeleton().path("project").path("title").asText());
        assertEquals("array<object>", result.skeleton().path("cards").path("$type").asText());
    }

    @Test
    void readStructureBuildsUnionSkeletonForObjectArraysAtNestedPath() throws Exception {
        JsonResourceService jsonResourceService = mock(JsonResourceService.class);
        JsonStructureServiceImpl service = new JsonStructureServiceImpl(objectMapper, jsonResourceService);

        JsonNode root = objectMapper.readTree("""
                {
                  "cards": [
                    {
                      "id": "c1",
                      "title": "Intro",
                      "body": [
                        {
                          "type": "paragraph",
                          "text": "Hello"
                        }
                      ]
                    },
                    {
                      "id": "c2",
                      "title": "Summary",
                      "done": true
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

        var result = service.readStructure(7L, 13L, 55L, 0L, "/cards", 4, 2, 8);

        assertEquals("/cards", result.path());
        assertEquals("array", result.nodeType());
        assertEquals(2, result.children().size());
        assertEquals("/cards/0", result.children().get(0));
        assertEquals("/cards/1", result.children().get(1));
        assertEquals("array<object>", result.skeleton().path("$type").asText());
        assertEquals("string", result.skeleton().path("$itemSkeleton").path("id").asText());
        assertEquals("array<object>", result.skeleton().path("$itemSkeleton").path("body").asText());
        assertEquals("boolean", result.skeleton().path("$itemSkeleton").path("done").asText());
    }
}
