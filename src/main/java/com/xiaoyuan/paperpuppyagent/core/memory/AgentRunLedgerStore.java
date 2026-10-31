package com.xiaoyuan.paperpuppyagent.core.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppyagent.config.AgentWorkMemoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阶段二 MVP：单进程、run-scoped 的 AgentRunLedger。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRunLedgerStore {
    private final ObjectMapper om;
    private final AgentWorkMemoryProperties properties;

    private final Map<String, AgentRunLedger> ledgersByRun = new ConcurrentHashMap<>();

    public void createRun(String agentRunId, String goal) {
        if (!properties.isEnabled()) {
            return;
        }
        String runId = normalize(agentRunId);
        if (runId.isBlank()) {
            return;
        }
        ledgersByRun.put(runId, new AgentRunLedger(runId, cap(goal)));
    }

    public Optional<AgentRunLedger.Snapshot> snapshot(String agentRunId) {
        AgentRunLedger ledger = ledgersByRun.get(normalize(agentRunId));
        return ledger == null ? Optional.empty() : Optional.of(ledger.snapshot());
    }

    public int recordModelCall(String agentRunId) {
        AgentRunLedger ledger = ledgersByRun.get(normalize(agentRunId));
        return ledger == null ? 0 : ledger.recordModelCall();
    }

    public void clearRun(String agentRunId) {
        String runId = normalize(agentRunId);
        if (!runId.isBlank()) {
            ledgersByRun.remove(runId);
        }
    }

    public AgentRunLedger.TodoEntry addTodo(String agentRunId,
                                            String id,
                                            String text,
                                            String status,
                                            String note,
                                            String evidenceCallId) {
        AgentRunLedger ledger = requireLedger(agentRunId);
        AgentRunLedger.TodoEntry entry = ledger.addTodo(
                cap(id),
                capRequired(text, "todo text is required"),
                cap(status),
                cap(note),
                cap(evidenceCallId));
        ledger.trim(
                properties.getMaxLedgerTodos(),
                properties.getMaxLedgerFacts(),
                properties.getMaxLedgerDecisions(),
                properties.getMaxLedgerArtifacts());
        return entry;
    }

    public AgentRunLedger.TodoEntry markTodo(String agentRunId,
                                             String todoId,
                                             String status,
                                             String note,
                                             String evidenceCallId) {
        AgentRunLedger ledger = requireLedger(agentRunId);
        AgentRunLedger.TodoEntry entry = ledger.markTodo(
                capRequired(todoId, "todo_id is required"),
                cap(status),
                cap(note),
                cap(evidenceCallId));
        ledger.trim(
                properties.getMaxLedgerTodos(),
                properties.getMaxLedgerFacts(),
                properties.getMaxLedgerDecisions(),
                properties.getMaxLedgerArtifacts());
        return entry;
    }

    public void addFact(String agentRunId, String text, String sourceCallId) {
        AgentRunLedger ledger = requireLedger(agentRunId);
        ledger.addFact(capRequired(text, "fact text is required"), cap(sourceCallId));
        ledger.trim(
                properties.getMaxLedgerTodos(),
                properties.getMaxLedgerFacts(),
                properties.getMaxLedgerDecisions(),
                properties.getMaxLedgerArtifacts());
    }

    public void addDecision(String agentRunId, String text, String reason, String sourceCallId) {
        AgentRunLedger ledger = requireLedger(agentRunId);
        ledger.addDecision(capRequired(text, "decision text is required"), cap(reason), cap(sourceCallId));
        ledger.trim(
                properties.getMaxLedgerTodos(),
                properties.getMaxLedgerFacts(),
                properties.getMaxLedgerDecisions(),
                properties.getMaxLedgerArtifacts());
    }

    public void addArtifact(String agentRunId, AgentRunLedger.ArtifactEntry artifact) {
        AgentRunLedger ledger = requireLedger(agentRunId);
        ledger.addArtifact(artifact);
        ledger.trim(
                properties.getMaxLedgerTodos(),
                properties.getMaxLedgerFacts(),
                properties.getMaxLedgerDecisions(),
                properties.getMaxLedgerArtifacts());
    }

    public Optional<ToolCallObservation> recordToolCall(String agentRunId,
                                                        String callId,
                                                        String toolName,
                                                        JsonNode args,
                                                        JsonNode result,
                                                        StoredToolResult storedResult) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        AgentRunLedger ledger = ledgersByRun.get(normalize(agentRunId));
        if (ledger == null) {
            return Optional.empty();
        }
        boolean ok = !isToolError(result);
        int resultChars = serializedLength(result);
        String rawRef = storedResult == null ? "" : storedResult.callId();
        Long documentId = longField(result, "document_id");
        Long fileId = longField(result, "file_id");
        String revision = textField(result, "revision");
        String errorCode = ok ? "" : textField(result, "error_code");
        String readKey = ok ? readKey(toolName, args, result, documentId, fileId, revision) : "";
        Optional<AgentRunLedger.ToolCallEntry> duplicate = ledger.findReadByKey(readKey);
        String duplicateOfCallId = duplicate.map(AgentRunLedger.ToolCallEntry::callId).orElse("");
        String reuseHint = duplicate
                .map(prev -> "Same resource/revision/path was already read by call_id="
                        + prev.callId() + ". Reuse the visible summary or open that result with agent_tool_result_open if details are needed.")
                .orElse("");

        ledger.addToolCall(new AgentRunLedger.ToolCallEntry(
                normalize(callId),
                normalize(toolName),
                ok ? "success" : "error",
                summarizeToolCall(toolName, args, result, ok),
                rawRef,
                resultChars,
                errorCode,
                documentId,
                fileId,
                revision,
                readKey,
                duplicateOfCallId,
                reuseHint
        ), properties.getMaxLedgerRecentToolCalls(), properties.getMaxLedgerReuseHints());

        AgentRunLedger.ArtifactEntry artifact = artifactFromResult(result, normalize(callId));
        if (artifact != null) {
            ledger.addArtifact(artifact);
        }
        ledger.trim(
                properties.getMaxLedgerTodos(),
                properties.getMaxLedgerFacts(),
                properties.getMaxLedgerDecisions(),
                properties.getMaxLedgerArtifacts());
        return Optional.of(new ToolCallObservation(readKey, duplicateOfCallId, reuseHint));
    }

    public record ToolCallObservation(
            String readKey,
            String duplicateOfCallId,
            String reuseHint
    ) {
        public boolean hasDuplicateRead() {
            return !Objects.toString(duplicateOfCallId, "").isBlank();
        }
    }

    private AgentRunLedger requireLedger(String agentRunId) {
        String runId = normalize(agentRunId);
        if (runId.isBlank()) {
            throw new IllegalArgumentException("missing agentRunId");
        }
        AgentRunLedger ledger = ledgersByRun.get(runId);
        if (ledger == null) {
            throw new IllegalArgumentException("agent run ledger not found: " + runId);
        }
        return ledger;
    }

    private AgentRunLedger.ArtifactEntry artifactFromResult(JsonNode result, String sourceCallId) {
        if (result == null || !result.isObject()) {
            return null;
        }
        Long documentId = longField(result, "document_id");
        Long fileId = longField(result, "file_id");
        String revision = textField(result, "revision");
        String path = firstTextField(result, "path", "content_path", "entry_path");
        String kind = firstTextField(result, "document_type", "canonical_kind", "resource_kind", "file_type");
        if (kind.isBlank()) {
            kind = documentId != null ? "document" : (fileId != null ? "file" : "");
        }
        AgentRunLedger.ArtifactEntry artifact = new AgentRunLedger.ArtifactEntry(
                cap(kind),
                documentId,
                fileId,
                cap(path),
                cap(revision),
                cap(sourceCallId));
        return artifact.isEmpty() ? null : artifact;
    }

    private String summarizeToolCall(String toolName, JsonNode args, JsonNode result, boolean ok) {
        if (!ok) {
            String code = textField(result, "error_code");
            String message = textField(result, "message");
            return cap("failed"
                    + (code.isBlank() ? "" : ": " + code)
                    + (message.isBlank() ? "" : " - " + message));
        }
        StringBuilder sb = new StringBuilder();
        sb.append(Objects.toString(toolName, "tool")).append(" ok");
        Long documentId = longField(result, "document_id");
        Long fileId = longField(result, "file_id");
        String revision = textField(result, "revision");
        String path = firstTextField(result, "path", "content_path");
        if (documentId != null) {
            sb.append(", document_id=").append(documentId);
        }
        if (fileId != null) {
            sb.append(", file_id=").append(fileId);
        }
        if (!revision.isBlank()) {
            sb.append(", revision=").append(shorten(revision, 16));
        }
        if (!path.isBlank()) {
            sb.append(", path=").append(path);
        }
        if (args != null && args.isObject()) {
            String argPath = firstTextField(args, "path", "query");
            if (!argPath.isBlank() && !Objects.equals(argPath, path)) {
                sb.append(", arg=").append(shorten(argPath, 80));
            }
        }
        return cap(sb.toString());
    }

    private String readKey(String toolName,
                           JsonNode args,
                           JsonNode result,
                           Long documentId,
                           Long fileId,
                           String revision) {
        String tool = normalize(toolName);
        if (!isReadLikeTool(tool)) {
            return "";
        }
        String resource = documentId != null ? "document:" + documentId : (fileId != null ? "file:" + fileId : "");
        if (resource.isBlank()) {
            Long argDocumentId = longField(args, "document_id");
            Long argFileId = longField(args, "file_id");
            resource = argDocumentId != null ? "document:" + argDocumentId : (argFileId != null ? "file:" + argFileId : "");
        }
        if (resource.isBlank()) {
            return "";
        }
        String path = firstNonBlank(
                firstTextField(result, "path", "entry_path"),
                firstTextField(args, "path", "entry_path"));
        String window = readWindow(args, result);
        String rev = firstNonBlank(revision, textField(args, "expected_revision"));
        return cap(tool + "|" + resource + "|rev=" + rev + "|path=" + path + "|window=" + window);
    }

    private static boolean isReadLikeTool(String toolName) {
        return switch (Objects.toString(toolName, "")) {
            case "json_structure_read",
                    "json_subtree_read",
                    "document_read",
                    "file_content_read" -> true;
            default -> false;
        };
    }

    private static String readWindow(JsonNode args, JsonNode result) {
        String startFragment = firstNonBlank(textField(result, "start_fragment"), textField(args, "start_fragment"));
        String endFragment = firstNonBlank(textField(result, "end_fragment"), textField(args, "end_fragment"));
        String offset = firstNonBlank(textField(result, "offset"), textField(args, "offset"));
        String limit = firstNonBlank(textField(result, "limit"), textField(args, "limit"));
        JsonNode pageNumbers = args == null ? null : args.get("page_numbers");
        if (pageNumbers != null && pageNumbers.isArray() && !pageNumbers.isEmpty()) {
            return "pages=" + pageNumbers.toString();
        }
        JsonNode selectedPages = result == null ? null : result.get("selected_pages");
        if (selectedPages != null && selectedPages.isArray() && !selectedPages.isEmpty()) {
            return "pages=" + selectedPages.toString();
        }
        if (!startFragment.isBlank() || !endFragment.isBlank()) {
            return startFragment + ":" + endFragment;
        }
        if (!offset.isBlank() || !limit.isBlank()) {
            return offset + ":" + limit;
        }
        return "";
    }

    private int serializedLength(JsonNode node) {
        if (node == null) {
            return 0;
        }
        try {
            return om.writeValueAsString(node).length();
        } catch (Exception e) {
            return Objects.toString(node, "").length();
        }
    }

    private static Long longField(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.canConvertToLong()) {
            return value.asLong();
        }
        String text = value.asText("").trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String firstTextField(JsonNode node, String... fields) {
        if (fields == null) {
            return "";
        }
        for (String field : fields) {
            String value = textField(node, field);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String textField(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }

    private static boolean isToolError(JsonNode result) {
        if (result == null || !result.isObject()) {
            return false;
        }
        JsonNode err = result.get("error");
        if (err != null && err.isTextual() && !err.asText("").isBlank()) {
            return true;
        }
        JsonNode ok = result.get("success");
        return ok != null && ok.isBoolean() && !ok.asBoolean(true);
    }

    private String capRequired(String text, String message) {
        String value = cap(text);
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String cap(String text) {
        return shorten(Objects.toString(text, "").trim(), Math.max(properties.getMaxLedgerTextChars(), 80));
    }

    private static String shorten(String text, int max) {
        String value = Objects.toString(text, "").trim();
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(max, 0)) + "...(truncated,len=" + value.length() + ")";
    }

    private static String firstNonBlank(String first, String second) {
        String a = Objects.toString(first, "").trim();
        return a.isBlank() ? Objects.toString(second, "").trim() : a;
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").trim();
    }
}
