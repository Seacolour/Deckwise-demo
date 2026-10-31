package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.config.AgentWorkMemoryProperties;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunLedger;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunLedgerStore;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentToolResultStore;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolErrorSupport;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

/**
 * 让模型补充 run-scoped 工作记忆中的语义层信息。
 */
@Component
public class AgentLedgerUpdateTool extends Tool {
    public static final String TOOL_NAME = "agent_ledger_update";

    private final AgentRunLedgerStore ledgerStore;
    private final AgentToolResultStore toolResultStore;
    private final AgentWorkMemoryProperties properties;

    public AgentLedgerUpdateTool(ObjectMapper mapper,
                                 AgentRunLedgerStore ledgerStore,
                                 AgentToolResultStore toolResultStore,
                                 AgentWorkMemoryProperties properties) {
        super(mapper);
        this.ledgerStore = ledgerStore;
        this.toolResultStore = toolResultStore;
        this.properties = properties;
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public JsonNode execute(JsonNode arguments, ToolExecutionContext context) {
        if (!properties.isEnabled()) {
            return ToolErrorSupport.businessError(
                    mapper,
                    TOOL_NAME,
                    "WORK_MEMORY_DISABLED",
                    "agent work memory is disabled",
                    "Continue with normal tools; do not update the ledger.",
                    false);
        }
        String runId = context == null ? "" : Objects.toString(context.agentRunId(), "").trim();
        if (runId.isBlank()) {
            return ToolErrorSupport.businessError(
                    mapper,
                    TOOL_NAME,
                    "MISSING_AGENT_RUN",
                    "missing agentRunId for current tool execution context",
                    "This tool only works inside an active Agent run.",
                    false);
        }
        JsonNode updates = arguments == null ? null : arguments.get("updates");
        if (updates == null || !updates.isArray() || updates.size() == 0) {
            return ToolErrorSupport.businessError(
                    mapper,
                    TOOL_NAME,
                    "MISSING_UPDATES",
                    "updates must be a non-empty array",
                    "Pass one or more add_todo/add_fact/add_decision/mark_todo_* updates.",
                    false);
        }

        int maxUpdates = Math.max(properties.getMaxLedgerUpdateItems(), 1);
        if (updates.size() > maxUpdates) {
            return ToolErrorSupport.businessError(
                    mapper,
                    TOOL_NAME,
                    "TOO_MANY_UPDATES",
                    "too many ledger updates: " + updates.size(),
                    "Send at most " + maxUpdates + " concise updates at a time.",
                    false);
        }

        try {
            ArrayNode applied = mapper.createArrayNode();
            for (JsonNode update : updates) {
                if (update == null || !update.isObject()) {
                    continue;
                }
                ObjectNode appliedOne = applyOne(runId, update);
                if (appliedOne != null) {
                    applied.add(appliedOne);
                }
            }

            ObjectNode out = mapper.createObjectNode();
            out.put("success", true);
            out.put("applied_count", applied.size());
            out.set("applied", applied);
            ledgerStore.snapshot(runId).ifPresent(snapshot -> {
                ObjectNode counts = mapper.createObjectNode();
                counts.put("todos", snapshot.todos().size());
                counts.put("facts", snapshot.facts().size());
                counts.put("decisions", snapshot.decisions().size());
                counts.put("artifacts", snapshot.artifacts().size());
                counts.put("recent_tool_calls", snapshot.recentToolCalls().size());
                out.set("ledger_counts", counts);
            });
            return out;
        } catch (Exception e) {
            return ToolErrorSupport.businessError(
                    mapper,
                    TOOL_NAME,
                    "INVALID_LEDGER_UPDATE",
                    e.getMessage(),
                    "Use short text fields, valid todo_id values, and sourceCallId from the current run when provided.",
                    false);
        }
    }

    private ObjectNode applyOne(String runId, JsonNode update) {
        String op = firstText(update, "op", "operation").toLowerCase(Locale.ROOT);
        String sourceCallId = firstText(update, "source_call_id", "sourceCallId", "evidence_call_id", "evidenceCallId");
        validateSourceCallId(runId, sourceCallId);

        ObjectNode applied = mapper.createObjectNode();
        applied.put("op", op);
        switch (op) {
            case "add_todo" -> {
                AgentRunLedger.TodoEntry entry = ledgerStore.addTodo(
                        runId,
                        firstText(update, "todo_id", "todoId", "id"),
                        firstText(update, "text", "content"),
                        firstText(update, "status"),
                        firstText(update, "note"),
                        sourceCallId);
                applied.put("todo_id", entry.id());
                applied.put("status", entry.status());
            }
            case "mark_todo_done", "mark_todo_completed" -> {
                AgentRunLedger.TodoEntry entry = ledgerStore.markTodo(
                        runId,
                        firstText(update, "todo_id", "todoId", "id"),
                        "done",
                        firstText(update, "note"),
                        sourceCallId);
                applied.put("todo_id", entry.id());
                applied.put("status", entry.status());
            }
            case "mark_todo_in_progress" -> {
                AgentRunLedger.TodoEntry entry = ledgerStore.markTodo(
                        runId,
                        firstText(update, "todo_id", "todoId", "id"),
                        "in_progress",
                        firstText(update, "note"),
                        sourceCallId);
                applied.put("todo_id", entry.id());
                applied.put("status", entry.status());
            }
            case "mark_todo_blocked" -> {
                AgentRunLedger.TodoEntry entry = ledgerStore.markTodo(
                        runId,
                        firstText(update, "todo_id", "todoId", "id"),
                        "blocked",
                        firstText(update, "note"),
                        sourceCallId);
                applied.put("todo_id", entry.id());
                applied.put("status", entry.status());
            }
            case "add_fact" -> {
                String text = firstText(update, "text", "fact");
                ledgerStore.addFact(runId, text, sourceCallId);
                applied.put("text", text);
            }
            case "add_decision" -> {
                String text = firstText(update, "text", "decision");
                String reason = firstText(update, "reason");
                ledgerStore.addDecision(runId, text, reason, sourceCallId);
                applied.put("text", text);
                if (!reason.isBlank()) {
                    applied.put("reason", reason);
                }
            }
            case "add_artifact" -> {
                AgentRunLedger.ArtifactEntry artifact = new AgentRunLedger.ArtifactEntry(
                        firstText(update, "kind", "type"),
                        firstLong(update, "document_id", "documentId"),
                        firstLong(update, "file_id", "fileId"),
                        firstText(update, "path", "entry_path", "entryPath"),
                        firstText(update, "revision"),
                        sourceCallId);
                ledgerStore.addArtifact(runId, artifact);
                applied.put("kind", Objects.toString(artifact.kind(), ""));
                if (artifact.documentId() != null) {
                    applied.put("document_id", artifact.documentId());
                }
                if (artifact.fileId() != null) {
                    applied.put("file_id", artifact.fileId());
                }
            }
            default -> throw new IllegalArgumentException("unsupported ledger update op: " + op);
        }
        if (!sourceCallId.isBlank()) {
            applied.put("sourceCallId", sourceCallId);
        }
        return applied;
    }

    private void validateSourceCallId(String runId, String sourceCallId) {
        if (sourceCallId == null || sourceCallId.isBlank()) {
            return;
        }
        if (toolResultStore.find(runId, sourceCallId).isEmpty()) {
            throw new IllegalArgumentException("sourceCallId not found in current run: " + sourceCallId);
        }
    }

    private static Long firstLong(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node == null ? null : node.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.canConvertToLong()) {
                return value.asLong();
            }
            String text = value.asText("").trim();
            if (text.isBlank()) {
                continue;
            }
            try {
                return Long.parseLong(text);
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return "";
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                String text = value.asText("").trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }
}
