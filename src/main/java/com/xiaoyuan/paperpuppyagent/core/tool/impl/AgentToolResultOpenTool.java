package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.config.AgentWorkMemoryProperties;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentToolResultStore;
import com.xiaoyuan.paperpuppyagent.core.memory.StoredToolResult;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolErrorSupport;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 按 callId 打开当前 run 内已保存的工具结果。
 */
@Component
public class AgentToolResultOpenTool extends Tool {
    public static final String TOOL_NAME = "agent_tool_result_open";

    private final AgentToolResultStore toolResultStore;
    private final AgentWorkMemoryProperties properties;

    public AgentToolResultOpenTool(ObjectMapper mapper,
                                   AgentToolResultStore toolResultStore,
                                   AgentWorkMemoryProperties properties) {
        super(mapper);
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
                    "Use narrower read/locate tools instead of opening previous tool results.",
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

        String callId = firstText(arguments, "call_id", "callId");
        if (callId.isBlank()) {
            return ToolErrorSupport.businessError(
                    mapper,
                    TOOL_NAME,
                    "MISSING_CALL_ID",
                    "call_id is required",
                    "Use the call_id/callId from a recent compacted tool result.",
                    false);
        }

        return toolResultStore.find(runId, callId)
                .map(stored -> openStoredResult(stored, arguments))
                .orElseGet(() -> ToolErrorSupport.businessError(
                        mapper,
                        TOOL_NAME,
                        "TOOL_RESULT_NOT_FOUND",
                        "tool result not found in current run: " + callId,
                        "The result may belong to a previous run, may have been evicted, or was too large to store. Re-run a narrower read/locate tool if needed.",
                        false));
    }

    private JsonNode openStoredResult(StoredToolResult stored, JsonNode arguments) {
        String mode = normalizeMode(firstText(arguments, "mode"));
        String path = firstText(arguments, "path", "json_pointer", "jsonPointer");
        int maxChars = resolveMaxChars(arguments);
        JsonNode value = stored.result();

        ObjectNode out = mapper.createObjectNode();
        out.put("success", true);
        out.put("call_id", stored.callId());
        out.put("tool", stored.toolName());
        out.put("mode", mode);
        out.put("result_size_chars", stored.resultChars());
        out.put("max_chars", maxChars);

        if ("path".equals(mode)) {
            JsonNode selected = selectPath(value, path);
            if (selected == null || selected.isMissingNode()) {
                return ToolErrorSupport.businessError(
                        mapper,
                        TOOL_NAME,
                        "JSON_PATH_NOT_FOUND",
                        "path not found in stored tool result: " + path,
                        "Use mode=summary first to inspect top-level fields, then retry with a valid JSON Pointer path.",
                        false);
            }
            out.put("path", path);
            writeCappedValue(out, "value", selected, maxChars);
            return out;
        }

        if ("detail".equals(mode)) {
            writeCappedValue(out, "value", value, maxChars);
            return out;
        }

        out.set("summary", summarizeValue(value));
        writeCappedValue(out, "value_preview", value, maxChars);
        return out;
    }

    private JsonNode summarizeValue(JsonNode value) {
        ObjectNode summary = mapper.createObjectNode();
        if (value == null || value.isNull()) {
            summary.put("node_type", "null");
            return summary;
        }
        summary.put("node_type", value.getNodeType().name().toLowerCase(Locale.ROOT));
        if (value.isObject()) {
            ArrayNode fields = summary.putArray("top_level_fields");
            Iterator<Map.Entry<String, JsonNode>> iterator = value.fields();
            int count = 0;
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                count++;
                if (fields.size() < 40) {
                    fields.add(entry.getKey());
                }
                copyScalar(summary, value, entry.getKey());
            }
            summary.put("top_level_field_count", count);
        } else if (value.isArray()) {
            summary.put("items", value.size());
        } else if (value.isTextual()) {
            summary.put("text_chars", value.asText("").length());
        }
        return summary;
    }

    private void copyScalar(ObjectNode target, JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isTextual()) {
            String text = value.asText("").trim();
            if (!text.isBlank() && text.length() <= 200) {
                target.put(field, text);
            }
        } else if (value.isNumber() || value.isBoolean()) {
            target.set(field, value.deepCopy());
        }
    }

    private JsonNode selectPath(JsonNode value, String path) {
        String normalized = Objects.toString(path, "").trim();
        if (normalized.isBlank() || "/".equals(normalized)) {
            return value;
        }
        if (!normalized.startsWith("/")) {
            return MissingNode.getInstance();
        }
        return value == null ? MissingNode.getInstance() : value.at(normalized);
    }

    private void writeCappedValue(ObjectNode out, String fieldName, JsonNode value, int maxChars) {
        String serialized;
        try {
            serialized = mapper.writeValueAsString(value);
        } catch (Exception e) {
            out.put(fieldName + "_text", Objects.toString(value, ""));
            out.put("truncated", false);
            return;
        }
        if (serialized.length() <= maxChars) {
            out.set(fieldName, value == null ? mapper.nullNode() : value.deepCopy());
            out.put("truncated", false);
            return;
        }
        out.put(fieldName + "_json", serialized.substring(0, Math.max(maxChars, 0))
                + "...(truncated,len=" + serialized.length() + ")");
        out.put("truncated", true);
    }

    private int resolveMaxChars(JsonNode arguments) {
        int hardMax = Math.max(properties.getMaxOpenChars(), 1);
        int defaultMax = Math.min(Math.max(properties.getDefaultOpenMaxChars(), 1), hardMax);
        JsonNode node = arguments == null ? null : arguments.get("max_chars");
        if (node == null || !node.canConvertToInt()) {
            node = arguments == null ? null : arguments.get("maxChars");
        }
        if (node == null || !node.canConvertToInt()) {
            return defaultMax;
        }
        int requested = Math.max(node.asInt(defaultMax), 1);
        return Math.min(requested, hardMax);
    }

    private static String normalizeMode(String mode) {
        String normalized = Objects.toString(mode, "summary").trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "detail", "path" -> normalized;
            default -> "summary";
        };
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return "";
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual()) {
                String text = value.asText("").trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }
}
