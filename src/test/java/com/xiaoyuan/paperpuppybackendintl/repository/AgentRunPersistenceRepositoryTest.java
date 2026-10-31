package com.xiaoyuan.paperpuppybackendintl.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRunPersistenceRepositoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void compactLedgerSnapshotJsonKeepsDocumentStateWhenBudgetIsSmall() throws Exception {
        AgentRunPersistenceRepository repository = new AgentRunPersistenceRepository(null, objectMapper);
        String longText = "x".repeat(4_000);
        String raw = """
                {
                  "agentRunId": "run_1",
                  "goal": "%s",
                  "status": "running",
                  "createdAt": "2026-05-06T10:00:00Z",
                  "todos": [
                    {"id":"1","text":"read document","status":"done","note":"ok"},
                    {"id":"2","text":"edit introduction","status":"in_progress","note":"%s"}
                  ],
                  "facts": [{"text":"document_id=101", "sourceCallId":"call_save"}],
                  "decisions": [],
                  "artifacts": [
                    {"kind":"document","documentId":101,"revision":"1","sourceCallId":"call_read"}
                  ],
                  "recentToolCalls": [
                    {"callId":"call_read","tool":"document_read","status":"success","summary":"read document","documentId":101,"revision":"1","readKey":"document_read|document:101|rev=1|path=|window=","resultChars":5000},
                    {"callId":"call_save","tool":"document_save","status":"success","summary":"saved document","documentId":101,"revision":"2","resultChars":1200}
                  ],
                  "reuseHints": [],
                  "budget": {"modelCalls": 3, "toolCalls": 2, "toolResultOpenCalls": 0}
                }
                """.formatted(longText, longText);

        String projection = repository.compactLedgerSnapshotJson(
                raw, OffsetDateTime.parse("2026-05-06T11:00:00+08:00"), 6_000).orElseThrow();
        JsonNode parsed = objectMapper.readTree(projection);

        assertTrue(projection.length() <= 6_000);
        assertEquals("persisted_agent_work_memory_projection", parsed.path("kind").asText());
        assertEquals(101, parsed.path("activeDocument").path("documentId").asLong());
        assertEquals("2", parsed.path("activeDocument").path("revision").asText());
        assertEquals("document_text",
                parsed.path("artifactState").path("activeViews").path(0).path("viewType").asText());
        assertEquals("stale",
                parsed.path("artifactState").path("activeViews").path(0).path("status").asText());
        assertTrue(parsed.path("taskState").path("todos").isArray());

        String tinyProjection = repository.compactLedgerSnapshotJson(
                raw, OffsetDateTime.parse("2026-05-06T11:00:00+08:00"), 2_000).orElseThrow();
        assertTrue(tinyProjection.length() <= 2_000);
        assertEquals("tiny", objectMapper.readTree(tinyProjection).path("projection_level").asText());
    }

    @Test
    void documentSaveWriteImpactUsesDocumentWidePath() {
        AgentRunPersistenceRepository repository = new AgentRunPersistenceRepository(null, objectMapper);
        AgentRunPersistenceRepository.ToolCallEntry entry = AgentRunPersistenceRepository.ToolCallEntry.builder()
                .runId("run_1")
                .callId("call_save")
                .userId(1L)
                .sessionId(2L)
                .projectId(3L)
                .toolName("document_save")
                .status("success")
                .success(true)
                .argumentsJson("{\"document_id\":101}")
                .resultJson("{\"success\":true,\"document_id\":101,\"revision\":\"2\"}")
                .build();

        AgentRunPersistenceRepository.ArtifactWriteImpact impact =
                repository.extractArtifactWriteImpact(entry).orElseThrow();

        assertEquals(101L, impact.documentId());
        assertEquals("2", impact.newRevision());
        assertEquals("document_save", impact.toolName());
        assertEquals(List.of("/"), impact.modifiedPaths());
    }

    @Test
    void retiredStructuredDocumentToolIsNotTrackedAsWrite() {
        AgentRunPersistenceRepository repository = new AgentRunPersistenceRepository(null, objectMapper);
        AgentRunPersistenceRepository.ToolCallEntry entry = AgentRunPersistenceRepository.ToolCallEntry.builder()
                .toolName("json_document_patch")
                .status("success")
                .success(true)
                .argumentsJson("{\"document_id\":101}")
                .resultJson("{\"success\":true,\"document_id\":101}")
                .build();

        assertTrue(repository.extractArtifactWriteImpact(entry).isEmpty());
    }

    @Test
    void documentWideWriteInvalidatesAnyReadView() {
        AgentRunPersistenceRepository repository = new AgentRunPersistenceRepository(null, objectMapper);
        AgentRunPersistenceRepository.ArtifactWriteImpact impact =
                new AgentRunPersistenceRepository.ArtifactWriteImpact(
                        101L, null, "2", List.of("/"), "document_save", "call_save");
        var view = objectMapper.createObjectNode();
        view.put("documentId", 101);
        view.put("revision", "1");
        view.put("path", "/sections/8");

        assertTrue(repository.applyWriteImpactToView(
                view, impact, OffsetDateTime.parse("2026-05-07T10:00:00+08:00")));
        assertEquals("stale", view.path("status").asText());
        assertTrue(AgentRunPersistenceRepository.jsonPointerMayOverlap("/", "/sections/8"));
        assertFalse(AgentRunPersistenceRepository.jsonPointerMayOverlap(
                "/sections/4", "/sections/8"));
    }
}
