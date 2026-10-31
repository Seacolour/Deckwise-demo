package com.xiaoyuan.paperpuppyagent.core.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 单次 Agent run 内给模型看的工作清单。
 *
 * <p>它不是审计事实来源；审计事实仍来自工具事件和后续持久化链路。</p>
 */
public final class AgentRunLedger {
    private final String agentRunId;
    private final Instant createdAt;
    private String goal;
    private String status;
    private int todoSeq;
    private int modelCalls;
    private int toolCalls;
    private int toolResultOpenCalls;

    private final List<TodoEntry> todos = new ArrayList<>();
    private final List<TextEntry> facts = new ArrayList<>();
    private final List<DecisionEntry> decisions = new ArrayList<>();
    private final List<ArtifactEntry> artifacts = new ArrayList<>();
    private final List<ToolCallEntry> recentToolCalls = new ArrayList<>();
    private final List<ReuseHintEntry> reuseHints = new ArrayList<>();
    private final Map<String, ToolCallEntry> readIndexByKey = new LinkedHashMap<>();

    AgentRunLedger(String agentRunId, String goal) {
        this.agentRunId = Objects.toString(agentRunId, "").trim();
        this.goal = Objects.toString(goal, "").trim();
        this.status = "running";
        this.createdAt = Instant.now();
    }

    synchronized TodoEntry addTodo(String id, String text, String status, String note, String evidenceCallId) {
        String cleanText = Objects.toString(text, "").trim();
        if (cleanText.isBlank()) {
            throw new IllegalArgumentException("todo text is required");
        }
        String todoId = Objects.toString(id, "").trim();
        if (todoId.isBlank()) {
            todoId = "todo_" + (++todoSeq);
        }
        TodoEntry entry = new TodoEntry(
                todoId,
                cleanText,
                normalizeStatus(status, "pending"),
                Objects.toString(note, "").trim(),
                Objects.toString(evidenceCallId, "").trim(),
                Instant.now());
        int existing = indexOfTodo(todoId);
        if (existing >= 0) {
            todos.set(existing, entry);
        } else {
            todos.add(entry);
        }
        return entry;
    }

    synchronized TodoEntry markTodo(String todoId, String status, String note, String evidenceCallId) {
        String id = Objects.toString(todoId, "").trim();
        if (id.isBlank()) {
            throw new IllegalArgumentException("todo_id is required");
        }
        int idx = indexOfTodo(id);
        if (idx < 0) {
            throw new IllegalArgumentException("todo not found: " + id);
        }
        TodoEntry prev = todos.get(idx);
        TodoEntry next = new TodoEntry(
                prev.id(),
                prev.text(),
                normalizeStatus(status, prev.status()),
                firstNonBlank(note, prev.note()),
                firstNonBlank(evidenceCallId, prev.evidenceCallId()),
                Instant.now());
        todos.set(idx, next);
        return next;
    }

    synchronized void addFact(String text, String sourceCallId) {
        facts.add(new TextEntry(
                Objects.toString(text, "").trim(),
                Objects.toString(sourceCallId, "").trim(),
                Instant.now()));
    }

    synchronized void addDecision(String text, String reason, String sourceCallId) {
        decisions.add(new DecisionEntry(
                Objects.toString(text, "").trim(),
                Objects.toString(reason, "").trim(),
                Objects.toString(sourceCallId, "").trim(),
                Instant.now()));
    }

    synchronized void addArtifact(ArtifactEntry artifact) {
        if (artifact == null || artifact.isEmpty()) {
            return;
        }
        for (int i = 0; i < artifacts.size(); i++) {
            ArtifactEntry existing = artifacts.get(i);
            if (existing.sameIdentity(artifact)) {
                artifacts.set(i, artifact);
                return;
            }
        }
        artifacts.add(artifact);
    }

    synchronized Optional<ToolCallEntry> findReadByKey(String readKey) {
        String key = Objects.toString(readKey, "").trim();
        if (key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(readIndexByKey.get(key));
    }

    synchronized int recordModelCall() {
        modelCalls++;
        return modelCalls;
    }

    synchronized void addToolCall(ToolCallEntry entry, int maxRecent, int maxReuseHints) {
        if (entry == null) {
            return;
        }
        toolCalls++;
        if ("agent_tool_result_open".equals(entry.tool())) {
            toolResultOpenCalls++;
        }
        recentToolCalls.add(entry);
        int max = Math.max(maxRecent, 1);
        while (recentToolCalls.size() > max) {
            recentToolCalls.remove(0);
        }
        String readKey = Objects.toString(entry.readKey(), "").trim();
        if (!readKey.isBlank() && "success".equals(entry.status())) {
            if (Objects.toString(entry.duplicateOfCallId(), "").isBlank()) {
                readIndexByKey.putIfAbsent(readKey, entry);
            } else {
                reuseHints.add(new ReuseHintEntry(
                        entry.callId(),
                        entry.duplicateOfCallId(),
                        entry.tool(),
                        readKey,
                        entry.reuseHint(),
                        Instant.now()));
                int reuseMax = Math.max(maxReuseHints, 1);
                while (reuseHints.size() > reuseMax) {
                    reuseHints.remove(0);
                }
            }
        }
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(
                agentRunId,
                goal,
                status,
                createdAt,
                List.copyOf(todos),
                List.copyOf(facts),
                List.copyOf(decisions),
                List.copyOf(artifacts),
                List.copyOf(recentToolCalls),
                List.copyOf(reuseHints),
                new BudgetSnapshot(modelCalls, toolCalls, toolResultOpenCalls));
    }

    synchronized void trim(int maxTodos, int maxFacts, int maxDecisions, int maxArtifacts) {
        trimHead(todos, maxTodos);
        trimHead(facts, maxFacts);
        trimHead(decisions, maxDecisions);
        trimHead(artifacts, maxArtifacts);
    }

    private int indexOfTodo(String id) {
        for (int i = 0; i < todos.size(); i++) {
            if (Objects.equals(todos.get(i).id(), id)) {
                return i;
            }
        }
        return -1;
    }

    private static void trimHead(List<?> list, int max) {
        int limit = Math.max(max, 1);
        while (list.size() > limit) {
            list.remove(0);
        }
    }

    private static String normalizeStatus(String value, String fallback) {
        String s = Objects.toString(value, "").trim().toLowerCase();
        return switch (s) {
            case "pending", "in_progress", "done", "completed", "blocked" -> s;
            default -> Objects.toString(fallback, "pending");
        };
    }

    private static String firstNonBlank(String value, String fallback) {
        String s = Objects.toString(value, "").trim();
        return s.isBlank() ? Objects.toString(fallback, "") : s;
    }

    public record Snapshot(
            String agentRunId,
            String goal,
            String status,
            Instant createdAt,
            List<TodoEntry> todos,
            List<TextEntry> facts,
            List<DecisionEntry> decisions,
            List<ArtifactEntry> artifacts,
            List<ToolCallEntry> recentToolCalls,
            List<ReuseHintEntry> reuseHints,
            BudgetSnapshot budget
    ) {
    }

    public record TodoEntry(
            String id,
            String text,
            String status,
            String note,
            String evidenceCallId,
            Instant updatedAt
    ) {
    }

    public record TextEntry(
            String text,
            String sourceCallId,
            Instant createdAt
    ) {
    }

    public record DecisionEntry(
            String text,
            String reason,
            String sourceCallId,
            Instant createdAt
    ) {
    }

    public record ArtifactEntry(
            String kind,
            Long documentId,
            Long fileId,
            String path,
            String revision,
            String sourceCallId
    ) {
        boolean isEmpty() {
            return Objects.toString(kind, "").isBlank()
                    && documentId == null
                    && fileId == null
                    && Objects.toString(path, "").isBlank()
                    && Objects.toString(revision, "").isBlank();
        }

        boolean sameIdentity(ArtifactEntry other) {
            if (other == null) {
                return false;
            }
            return Objects.equals(kind, other.kind)
                    && Objects.equals(documentId, other.documentId)
                    && Objects.equals(fileId, other.fileId)
                    && Objects.equals(path, other.path);
        }
    }

    public record ToolCallEntry(
            String callId,
            String tool,
            String status,
            String summary,
            String rawResultRef,
            int resultChars,
            String errorCode,
            Long documentId,
            Long fileId,
            String revision,
            String readKey,
            String duplicateOfCallId,
            String reuseHint
    ) {
    }

    public record ReuseHintEntry(
            String callId,
            String duplicateOfCallId,
            String tool,
            String readKey,
            String hint,
            Instant createdAt
    ) {
    }

    public record BudgetSnapshot(
            int modelCalls,
            int toolCalls,
            int toolResultOpenCalls
    ) {
    }
}
