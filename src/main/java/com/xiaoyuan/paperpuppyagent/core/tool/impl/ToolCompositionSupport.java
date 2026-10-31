package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolErrorSupport;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j
final class ToolCompositionSupport {

    private ToolCompositionSupport() {
    }

    static ObjectNode start(ObjectMapper mapper, String composition) {
        ObjectNode root = mapper.createObjectNode();
        root.put("success", true);
        root.put("composition", composition);
        root.set("steps", mapper.createArrayNode());
        return root;
    }

    static ArrayNode steps(ObjectNode root) {
        return (ArrayNode) root.withArray("steps");
    }

    static StepResult runStep(ObjectMapper mapper,
                              String composition,
                              Tool tool,
                              JsonNode arguments,
                              ToolExecutionContext context) {
        String toolName = tool == null ? "" : tool.name();
        long startNs = System.nanoTime();
        log.info("[ToolComposition] step start: composition={}, tool={}", composition, toolName);
        try {
            JsonNode result = tool.execute(arguments, context);
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            ObjectNode summary = stepSummary(mapper, toolName, result, elapsedMs);
            log.info("[ToolComposition] step ok: composition={}, tool={}, elapsedMs={}, success={}",
                    composition, toolName, elapsedMs, summary.path("success").asBoolean(false));
            return new StepResult(result, summary);
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            ObjectNode error = ToolErrorSupport.fromException(mapper, toolName, e);
            ObjectNode summary = stepSummary(mapper, toolName, error, elapsedMs);
            log.warn("[ToolComposition] step failed: composition={}, tool={}, elapsedMs={}, err={}",
                    composition, toolName, elapsedMs, e.getMessage());
            return new StepResult(error, summary);
        }
    }

    static ObjectNode stepSummary(ObjectMapper mapper, String toolName, JsonNode result, long elapsedMs) {
        ObjectNode summary = mapper.createObjectNode();
        summary.put("tool", Objects.toString(toolName, ""));
        summary.put("success", isSuccess(result));
        summary.put("elapsed_ms", elapsedMs);
        copyScalar(summary, result, "document_id");
        copyScalar(summary, result, "revision");
        copyScalar(summary, result, "matches_count");
        copyScalar(summary, result, "total_cards");
        copyScalar(summary, result, "sheet_key");
        copyScalar(summary, result, "error_code");
        copyScalar(summary, result, "message");
        return summary;
    }

    static void copyScalar(ObjectNode target, JsonNode source, String field) {
        if (source == null || target == null || field == null || field.isBlank()) {
            return;
        }
        JsonNode value = source.get(field);
        if (value != null && value.isValueNode()) {
            target.set(field, value.deepCopy());
        }
    }

    static boolean isSuccess(JsonNode result) {
        return result != null && result.path("success").asBoolean(false);
    }

    static ObjectNode needsDecision(ObjectMapper mapper,
                                    ObjectNode root,
                                    String reason,
                                    String message) {
        root.put("success", true);
        root.put("needs_model_decision", true);
        root.put("decision_reason", Objects.toString(reason, ""));
        if (message != null && !message.isBlank()) {
            root.put("message", message);
        }
        return root;
    }

    static ObjectNode fail(ObjectNode root,
                           String failedStep,
                           JsonNode failedResult) {
        root.put("success", false);
        root.put("failed_step", Objects.toString(failedStep, ""));
        if (failedResult != null && failedResult.isObject()) {
            root.set("error_result", failedResult.deepCopy());
            copyScalar(root, failedResult, "error_code");
            copyScalar(root, failedResult, "message");
        }
        return root;
    }

    record StepResult(JsonNode result, ObjectNode summary) {
    }
}
