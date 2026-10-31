package com.xiaoyuan.paperpuppyagent.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openai.models.responses.ResponseInputItem;
import com.xiaoyuan.paperpuppyagent.config.AgentToolResultProperties;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunLedgerStore;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentToolResultStore;
import com.xiaoyuan.paperpuppyagent.core.memory.StoredToolResult;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Converts raw tool results into compact model input plus extracted media.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolResultProcessor {
    private final ObjectMapper om;
    private final AgentConversationMapper conversationMapper;
    private final AgentToolResultProperties toolResultProperties;
    private final AgentToolResultStore toolResultStore;
    private final AgentRunLedgerStore ledgerStore;

    public ProcessedToolResult process(ToolExecutionContext toolContext,
                                       String callId,
                                       String name,
                                       JsonNode args,
                                       JsonNode result) {
        JsonNode safeResult = result == null ? om.createObjectNode() : result;
        String resultJson = serializeResult(callId, name, safeResult);
        StoredToolResult storedResult = toolResultStore.store(
                toolContext == null ? "" : toolContext.agentRunId(),
                callId,
                name,
                safeResult).orElse(null);
        AgentRunLedgerStore.ToolCallObservation ledgerObservation = ledgerStore.recordToolCall(
                toolContext == null ? "" : toolContext.agentRunId(),
                callId,
                name,
                args,
                safeResult,
                storedResult).orElse(null);
        JsonNode modelVisibleResult = compactForModel(name, safeResult, storedResult);
        modelVisibleResult = withLedgerObservation(modelVisibleResult, ledgerObservation);
        boolean compactedForModel = modelVisibleResult != null
                && modelVisibleResult.isObject()
                && modelVisibleResult.path("_compacted_for_model").asBoolean(false);
        return new ProcessedToolResult(
                safeResult,
                resultJson,
                conversationMapper.functionCallOutputItem(callId, modelVisibleResult),
                conversationMapper.extractImageUrlsFromToolResult(safeResult),
                conversationMapper.extractFileUrlsFromToolResult(safeResult),
                ledgerObservation,
                compactedForModel,
                summarizeToolResult(safeResult));
    }

    private String serializeResult(String callId, String name, JsonNode result) {
        try {
            return om.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[Agent][Tool] 结果序列化失败: callId={}, name={}, error={}", callId, name, e.getMessage());
            ObjectNode errNode = om.createObjectNode();
            errNode.put("error", "serialization_failed");
            errNode.put("message", Objects.toString(e.getMessage(), "serialization failed"));
            return errNode.toString();
        }
    }

    JsonNode compactForModel(String toolName, JsonNode result) {
        return compactForModel(toolName, result, null);
    }

    JsonNode compactForModel(String toolName, JsonNode result, StoredToolResult storedResult) {
        if (!toolResultProperties.isCompactForModel() || result == null || isToolError(result)) {
            return result;
        }
        String raw;
        try {
            raw = om.writeValueAsString(result);
        } catch (Exception e) {
            return result;
        }
        int maxChars = effectiveModelVisibleMaxChars(result,
                Math.max(toolResultProperties.getModelVisibleMaxChars(), 0));
        if (maxChars <= 0 || raw.length() <= maxChars) {
            return result;
        }

        ObjectNode compact = om.createObjectNode();
        compact.put("success", result.path("success").asBoolean(true));
        compact.put("tool", Objects.toString(toolName, ""));
        if (storedResult != null) {
            compact.put("call_id", storedResult.callId());
            compact.put("raw_result_ref", storedResult.callId());
            compact.put("can_open_with", "agent_tool_result_open");
        }
        compact.put("_compacted_for_model", true);
        compact.put("original_result_chars", raw.length());
        compact.put("model_visible_max_chars", maxChars);
        copyScalarFields(compact, result,
                "document_id", "file_id", "file_name", "file_type", "title",
                "document_type", "canonical_kind", "entry_path", "revision", "version",
                "working_version", "source", "line_mode", "total_lines", "start_fragment",
                "end_fragment", "returned_fragments", "total_fragments", "truncated",
                "mode", "book_key", "node_key", "query", "provider",
                "composition", "needs_model_decision", "decision_reason", "failed_step",
                "offset", "limit",
                "result_count", "estimated_model_chars", "matches_count",
                "case_sensitive", "include_paths", "max_results", "context_chars");

        if (result.path("composition").isObject()) {
            compactRuntimeComposition(compact, result);
        } else switch (Objects.toString(toolName, "")) {
            case "document_read" -> compactTextField(compact, result, "content", "content_preview");
            case "file_content_read" -> {
                compactTextField(compact, result, "content", "content_preview");
                int endFragment = result.path("end_fragment").asInt(0);
                int totalFragments = result.path("total_fragments").asInt(0);
                if (endFragment > 0 && totalFragments > endFragment) {
                    compact.put("next_start_fragment", endFragment + 1);
                }
            }
            default -> compactGenericPreview(compact, result);
        }

        compact.put("next_step_hint", nextStepHint(toolName, result));
        log.info("[Agent][Tool] 模型回灌结果已压缩: tool={}, originalChars={}, compactChars={}",
                toolName, raw.length(), serializedLength(compact));
        return compact;
    }

    private JsonNode withLedgerObservation(JsonNode modelVisibleResult,
                                           AgentRunLedgerStore.ToolCallObservation observation) {
        if (observation == null || !observation.hasDuplicateRead() || modelVisibleResult == null
                || !modelVisibleResult.isObject()) {
            return modelVisibleResult;
        }
        ObjectNode out = ((ObjectNode) modelVisibleResult).deepCopy();
        ObjectNode hint = out.putObject("read_reuse_hint");
        hint.put("duplicate_of_call_id", observation.duplicateOfCallId());
        hint.put("message", observation.reuseHint());
        hint.put("can_open_previous_with", "agent_tool_result_open");
        return out;
    }

    private int effectiveModelVisibleMaxChars(JsonNode result, int defaultMaxChars) {
        if (defaultMaxChars <= 0) {
            return defaultMaxChars;
        }
        if (result != null && result.path("composition").isObject()) {
            return Math.max(defaultMaxChars, defaultMaxChars * compositionStepCount(result));
        }
        return defaultMaxChars;
    }

    private int compositionStepCount(JsonNode result) {
        JsonNode steps = result == null ? null : result.path("steps");
        if (steps != null && steps.isArray() && !steps.isEmpty()) {
            return Math.max(1, steps.size());
        }
        JsonNode nestedSteps = result == null ? null : result.path("composition").path("steps");
        if (nestedSteps != null && nestedSteps.isArray() && !nestedSteps.isEmpty()) {
            return Math.max(1, nestedSteps.size());
        }
        String status = result == null ? "" : result.path("composition").path("status").asText("");
        String then = result == null ? "" : result.path("composition").path("then").asText("");
        if ("completed".equals(status) && !then.isBlank()) {
            return 2;
        }
        return 1;
    }

    private void compactRuntimeComposition(ObjectNode compact, JsonNode result) {
        copyObjectField(compact, result, "composition");
        copyArrayField(compact, result, "steps");
        JsonNode nested = result.path("result");
        if (!nested.isObject()) {
            compactGenericPreview(compact, result);
            return;
        }

        JsonNode source = nested.path("source");
        if (source.isObject()) {
            copyScalarFields(compact, source,
                    "document_id", "file_id", "revision",
                    "path", "node_type", "resource_kind", "source_name",
                    "entry_path", "total_count");
            compactArrayField(compact, source, "children", "source_children_preview");
        }

        JsonNode next = nested.path("next");
        if (next.isObject()) {
            copyScalarFields(compact, next,
                    "document_id", "file_id", "revision",
                    "path", "node_type", "resource_kind",
                    "source_name", "entry_path", "file_kind", "mime_type", "size_bytes");
            if (next.has("value")) {
                compact.set("value_preview", previewNode(next.path("value")));
            }
        }

        JsonNode nextError = nested.path("next_error");
        if (nextError.isObject()) {
            copyObjectField(compact, nested, "next_error");
        }
        addRuntimeCompositionOpenPaths(compact, result);
    }

    private void addRuntimeCompositionOpenPaths(ObjectNode compact, JsonNode result) {
        JsonNode nested = result.path("result");
        if (!nested.isObject()) {
            return;
        }
        ObjectNode open = compact.putObject("raw_result_open_paths");
        String callId = compact.path("raw_result_ref").asText("");
        if (!callId.isBlank()) {
            open.put("call_id", callId);
        }
        open.put("tool", "agent_tool_result_open");
        ObjectNode paths = open.putObject("available_paths");
        JsonNode source = nested.path("source");
        if (source.isObject()) {
            paths.put("source_result", "/result/source");
            if (source.path("children").isArray()) {
                paths.put("source_children", "/result/source/children");
            }
        }
        JsonNode next = nested.path("next");
        String recommendedPath = "";
        if (next.isObject()) {
            paths.put("next_result", "/result/next");
            if (next.has("value")) {
                paths.put("next_value", "/result/next/value");
                if (recommendedPath.isBlank()) {
                    recommendedPath = "/result/next/value";
                }
            }
        }
        if (recommendedPath.isBlank()) {
            if (paths.has("source_children")) {
                recommendedPath = "/result/source/children";
            } else if (paths.has("source_result")) {
                recommendedPath = "/result/source";
            } else {
                recommendedPath = "/";
            }
        }
        ObjectNode recommended = open.putObject("recommended");
        recommended.put("mode", "path");
        recommended.put("path", recommendedPath);
        recommended.put("max_chars", 12000);
        if (!callId.isBlank()) {
            recommended.put("call_id", callId);
        }
    }

    private void compactGenericPreview(ObjectNode compact, JsonNode result) {
        compact.set("result_preview", previewNode(result));
    }

    private JsonNode previewNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return om.nullNode();
        }
        if (node.isTextual()) {
            return om.getNodeFactory().textNode(truncate(node.asText(""), textPreviewChars()));
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.deepCopy();
        }
        if (node.isArray()) {
            ArrayNode out = om.createArrayNode();
            int limit = Math.min(node.size(), arrayPreviewItems());
            for (int i = 0; i < limit; i++) {
                out.add(previewNode(node.get(i)));
            }
            if (node.size() > limit) {
                ObjectNode marker = om.createObjectNode();
                marker.put("_truncated_items", node.size() - limit);
                out.add(marker);
            }
            return out;
        }
        if (node.isObject()) {
            ObjectNode out = om.createObjectNode();
            int limit = Math.max(arrayPreviewItems() * 3, 12);
            int[] count = {0};
            node.fields().forEachRemaining(entry -> {
                if (count[0] >= limit) {
                    return;
                }
                count[0]++;
                JsonNode value = entry.getValue();
                if (value != null && value.isTextual()) {
                    out.put(entry.getKey(), truncate(value.asText(""), textPreviewChars()));
                } else if (value != null && (value.isNumber() || value.isBoolean() || value.isNull())) {
                    out.set(entry.getKey(), value.deepCopy());
                } else if (value != null && serializedLength(value) <= textPreviewChars()) {
                    out.set(entry.getKey(), value.deepCopy());
                } else if (value != null) {
                    ObjectNode marker = om.createObjectNode();
                    marker.put("_node_type", value.getNodeType().name().toLowerCase());
                    marker.put("_estimated_chars", serializedLength(value));
                    out.set(entry.getKey(), marker);
                }
            });
            int objectSize = node.size();
            if (objectSize > limit) {
                out.put("_truncated_fields", objectSize - limit);
            }
            return out;
        }
        return om.getNodeFactory().textNode(truncate(node.toString(), textPreviewChars()));
    }

    private void copyScalarFields(ObjectNode target, JsonNode source, String... fields) {
        for (String field : fields) {
            JsonNode value = source.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isTextual()) {
                String text = value.asText("").trim();
                if (!text.isBlank()) {
                    target.put(field, text);
                }
            } else if (value.isNumber() || value.isBoolean()) {
                target.set(field, value.deepCopy());
            }
        }
    }

    private void copyObjectField(ObjectNode target, JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value != null && value.isObject()) {
            target.set(field, value.deepCopy());
        }
    }

    private void copyArrayField(ObjectNode target, JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value != null && value.isArray()) {
            target.set(field, value.deepCopy());
        }
    }

    private void compactArrayField(ObjectNode target, JsonNode source, String sourceField, String targetField) {
        JsonNode value = source.get(sourceField);
        if (value == null || !value.isArray()) {
            return;
        }
        target.set(targetField, previewNode(value));
        target.put(sourceField + "_total_items", value.size());
    }

    private void compactTextField(ObjectNode target, JsonNode source, String sourceField, String targetField) {
        String content = source.path(sourceField).asText("");
        if (content.isBlank()) {
            return;
        }
        target.put(sourceField + "_chars", content.length());
        target.put(targetField, truncate(content, textPreviewChars()));
    }

    private int textPreviewChars() {
        return Math.max(toolResultProperties.getTextPreviewChars(), 500);
    }

    private int arrayPreviewItems() {
        return Math.max(toolResultProperties.getArrayPreviewItems(), 1);
    }

    private int serializedLength(JsonNode node) {
        try {
            return om.writeValueAsString(node).length();
        } catch (Exception e) {
            return Objects.toString(node, "").length();
        }
    }

    private static String nextStepHint(String toolName, JsonNode result) {
        JsonNode composition = result == null ? null : result.path("composition");
        if (composition != null && composition.isObject()) {
            String status = composition.path("status").asText("");
            String then = composition.path("then").asText("");
            if ("completed".equals(status) && "json_subtree_read".equals(then)) {
                return "Runtime composition completed. For the full JSON subtree, use agent_tool_result_open with path=/result/next/value on this call_id.";
            }
            if ("skipped".equals(status) || "rejected".equals(status)) {
                return "Runtime composition did not run the next tool. Use raw_result_open_paths.available_paths to inspect source matches/candidates, then call a narrower tool manually.";
            }
        }
        return switch (Objects.toString(toolName, "")) {
            case "document_read" ->
                    "Full content omitted from this turn. Use document_locate for exact spans, then document_diff/document_save for edits.";
            case "file_content_read" ->
                    "Only a content preview is visible. If more text is needed, call file_content_read again with start_fragment=end_fragment+1.";
            default ->
                    "Tool result was compacted for model context. Re-run a narrower read/locate tool if exact details are needed.";
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "...(truncated,len=" + t.length() + ")";
    }

    private String summarizeToolResult(JsonNode result) {
        if (result == null) {
            return "null";
        }
        try {
            String raw = om.writeValueAsString(result);
            return truncate(raw, 300);
        } catch (Exception e) {
            return "unserializable(" + e.getMessage() + ")";
        }
    }

    private static boolean isToolError(JsonNode result) {
        if (result == null || !result.isObject()) {
            return false;
        }
        // SkillExecutor 失败会返回 {"error": "...", "message": "...", ...}
        JsonNode err = result.get("error");
        if (err != null && err.isTextual() && !err.asText("").isBlank()) {
            return true;
        }
        // 约定：success=false 也视为失败
        JsonNode ok = result.get("success");
        return ok != null && ok.isBoolean() && !ok.asBoolean(true);
    }

    public record ProcessedToolResult(
            JsonNode result,
            String resultJson,
            ResponseInputItem appendedInput,
            List<String> imageUrls,
            List<String> fileUrls,
            AgentRunLedgerStore.ToolCallObservation ledgerObservation,
            boolean compactedForModel,
            String resultSummary) {
    }
}
