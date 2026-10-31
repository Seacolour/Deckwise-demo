package com.xiaoyuan.paperpuppyagent.core.composition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillCatalog;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillExecutor;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillFunction;
import com.xiaoyuan.paperpuppyagent.core.skill.runtime.SkillExecutionResult;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * Framework-level tool composition executor.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolCompositionRuntime {

    private final ObjectMapper om;
    private final SkillCatalog skillCatalog;
    private final SkillExecutor skillExecutor;
    private final ToolCompositionRegistry registry;

    public boolean hasCompose(JsonNode arguments) {
        return arguments != null && arguments.isObject() && arguments.path("compose").isObject();
    }

    public SkillExecutionResult execute(String sourceTool, JsonNode arguments, ToolExecutionContext context) {
        String source = normalize(sourceTool);
        JsonNode compose = arguments == null ? null : arguments.path("compose");
        String then = compose == null ? "" : normalize(compose.path("then").asText(""));
        String when = compose == null ? "" : normalize(compose.path("when").asText(""));
        log.info("[ToolCompositionRuntime] requested: source={}, then={}, when={}", source, then, when);

        if (writesData(source)) {
            return SkillExecutionResult.toolResult(rejectedWithoutSource(source, then, when,
                    "write_source_not_allowed",
                    "Tool composition V1 does not allow write tools as the source."));
        }

        ObjectNode sourceArgs = withoutCompose(arguments);
        ExecutedStep sourceStep = executeStep(source, sourceArgs, context);
        JsonNode sourceResult = sourceStep.result();
        if (isToolError(sourceResult)) {
            return SkillExecutionResult.toolResult(sourceResult);
        }

        Optional<ToolCompositionRule> maybeRule = registry.findRule(source, then, when);
        if (maybeRule.isEmpty()) {
            return SkillExecutionResult.toolResult(rejectedWithSource(source, then, when, sourceStep,
                    "unsupported_composition",
                    "This source/next tool composition is not whitelisted."));
        }
        ToolCompositionRule rule = maybeRule.get();
        if (rule.maxDepth() > 1) {
            return SkillExecutionResult.toolResult(rejectedWithSource(source, then, when, sourceStep,
                    "unsupported_depth",
                    "Tool composition V1 only supports one next tool."));
        }
        if (!rule.allowWriteTools() && writesData(rule.then())) {
            return SkillExecutionResult.toolResult(rejectedWithSource(source, then, when, sourceStep,
                    "write_next_not_allowed",
                    "Tool composition V1 does not allow write tools as the next step."));
        }

        AssertionOutcome assertion = assertRule(rule, sourceResult, compose);
        if (!assertion.ok()) {
            log.info("[ToolCompositionRuntime] assertion skipped: source={}, then={}, reason={}",
                    source, then, assertion.reason());
            return SkillExecutionResult.toolResult(skipped(rule, sourceStep, assertion));
        }

        ObjectNode nextArgs = buildNextArgs(rule, sourceArgs, sourceResult, compose, assertion.slots());
        ExecutedStep nextStep = executeStep(rule.then(), nextArgs, context);
        JsonNode nextResult = nextStep.result();
        if (isToolError(nextResult)) {
            return SkillExecutionResult.toolResult(failed(rule, sourceStep, nextStep, nextResult));
        }

        return SkillExecutionResult.toolResult(completed(rule, sourceStep, nextStep));
    }

    private ExecutedStep executeStep(String tool, JsonNode args, ToolExecutionContext context) {
        long startNs = System.nanoTime();
        SkillExecutionResult result = skillExecutor.execute(tool, args, context);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        JsonNode payload = result == null || result.payload() == null ? om.createObjectNode() : result.payload();
        log.info("[ToolCompositionRuntime] step done: tool={}, elapsedMs={}, success={}",
                tool, elapsedMs, !isToolError(payload));
        return new ExecutedStep(tool, args == null ? om.createObjectNode() : args.deepCopy(), payload, elapsedMs);
    }

    private AssertionOutcome assertRule(ToolCompositionRule rule,
                                        JsonNode sourceResult,
                                        JsonNode compose) {
        return switch (rule.when()) {
            case "declared_child_path" -> assertDeclaredJsonPath(sourceResult, compose);
            default -> new AssertionOutcome(false, "unsupported_assertion", om.createObjectNode());
        };
    }

    private AssertionOutcome assertDeclaredJsonPath(JsonNode sourceResult, JsonNode compose) {
        String requestedPath = compose.path("next_args").path("path").asText("").trim();
        if (requestedPath.isBlank()) {
            return new AssertionOutcome(false, "missing_next_path", om.createObjectNode());
        }
        if (!requestedPath.startsWith("/")) {
            return new AssertionOutcome(false, "invalid_json_pointer", om.createObjectNode());
        }

        String currentPath = sourceResult.path("path").asText("/").trim();
        if (currentPath.isBlank()) {
            currentPath = "/";
        }
        boolean declared = requestedPath.equals(currentPath);
        JsonNode children = sourceResult.path("children");
        if (!declared && children.isArray()) {
            for (JsonNode child : children) {
                if (requestedPath.equals(child.asText(""))) {
                    declared = true;
                    break;
                }
            }
        }
        if (!declared) {
            return new AssertionOutcome(false, "path_not_declared", om.createObjectNode());
        }
        ObjectNode slots = om.createObjectNode();
        slots.put("path", requestedPath);
        return new AssertionOutcome(true, "", slots);
    }

    private ObjectNode buildNextArgs(ToolCompositionRule rule,
                                     ObjectNode sourceArgs,
                                     JsonNode sourceResult,
                                     JsonNode compose,
                                     ObjectNode slots) {
        ObjectNode out = om.createObjectNode();
        long documentId = firstPositiveLong(sourceResult, sourceArgs, "document_id");
        if (documentId > 0) {
            out.put("document_id", documentId);
        }
        long fileId = firstPositiveLong(sourceResult, sourceArgs, "file_id");
        if (fileId > 0) {
            out.put("file_id", fileId);
        }
        if ("json_subtree_read".equals(rule.then())) {
            copyTextSlot(out, slots, "path", "path");
            copyIfPresent(out, compose.path("next_args"), "max_bytes");
            copyIfPresent(out, compose.path("next_args"), "max_nodes");
        }
        return out;
    }

    private ObjectNode completed(ToolCompositionRule rule, ExecutedStep sourceStep, ExecutedStep nextStep) {
        ObjectNode root = root(rule, "completed", "");
        root.set("steps", stepArray(sourceStep, nextStep));
        root.withObject("composition").set("steps", stepArray(sourceStep, nextStep));
        ObjectNode result = root.putObject("result");
        result.set("source", sourceStep.result().deepCopy());
        result.set("next", nextStep.result().deepCopy());
        root.put("needs_model_decision", false);
        copyScalars(root, nextStep.result(), "document_id", "file_id", "revision", "path", "node_type", "resource_kind", "source_name",
                "file_kind", "mime_type", "size_bytes");
        copyScalars(root, sourceStep.result(), "total_count");
        log.info("[ToolCompositionRuntime] completed: source={}, then={}, elapsedMs={}",
                rule.source(), rule.then(), sourceStep.elapsedMs() + nextStep.elapsedMs());
        return root;
    }

    private ObjectNode skipped(ToolCompositionRule rule, ExecutedStep sourceStep, AssertionOutcome assertion) {
        ObjectNode root = root(rule, "skipped", assertion.reason());
        root.set("steps", stepArray(sourceStep));
        root.withObject("composition").set("steps", stepArray(sourceStep));
        ObjectNode result = root.putObject("result");
        result.set("source", sourceStep.result().deepCopy());
        root.put("needs_model_decision", true);
        root.put("decision_reason", assertion.reason());
        copyScalars(root, sourceStep.result(), "document_id", "file_id", "revision", "path");
        JsonNode children = sourceStep.result().path("children");
        if (children.isArray()) {
            root.set("children", children.deepCopy());
        }
        return root;
    }

    private ObjectNode failed(ToolCompositionRule rule,
                              ExecutedStep sourceStep,
                              ExecutedStep nextStep,
                              JsonNode nextError) {
        ObjectNode root = root(rule, "failed", "next_tool_failed");
        root.put("success", false);
        root.put("failed_step", rule.then());
        root.set("steps", stepArray(sourceStep, nextStep));
        root.withObject("composition").set("steps", stepArray(sourceStep, nextStep));
        ObjectNode result = root.putObject("result");
        result.set("source", sourceStep.result().deepCopy());
        result.set("next_error", nextError.deepCopy());
        return root;
    }

    private ObjectNode rejectedWithSource(String source,
                                          String then,
                                          String when,
                                          ExecutedStep sourceStep,
                                          String reason,
                                          String message) {
        ObjectNode root = rejectedBase(source, then, when, reason, message);
        root.set("steps", stepArray(sourceStep));
        root.withObject("composition").set("steps", stepArray(sourceStep));
        root.putObject("result").set("source", sourceStep.result().deepCopy());
        copyScalars(root, sourceStep.result(), "document_id", "file_id", "revision", "path");
        return root;
    }

    private ObjectNode rejectedWithoutSource(String source,
                                             String then,
                                             String when,
                                             String reason,
                                             String message) {
        return rejectedBase(source, then, when, reason, message);
    }

    private ObjectNode rejectedBase(String source, String then, String when, String reason, String message) {
        ObjectNode root = om.createObjectNode();
        root.put("success", true);
        ObjectNode composition = root.putObject("composition");
        composition.put("attempted", true);
        composition.put("status", "rejected");
        composition.put("reason", reason);
        composition.put("source", source);
        composition.put("then", then);
        composition.put("when", when);
        composition.put("message", message);
        root.put("needs_model_decision", true);
        root.put("decision_reason", reason);
        return root;
    }

    private ObjectNode root(ToolCompositionRule rule, String status, String reason) {
        ObjectNode root = om.createObjectNode();
        root.put("success", true);
        ObjectNode composition = root.putObject("composition");
        composition.put("attempted", true);
        composition.put("status", status);
        if (!reason.isBlank()) {
            composition.put("reason", reason);
        }
        composition.put("source", rule.source());
        composition.put("then", rule.then());
        composition.put("when", rule.when());
        return root;
    }

    private ArrayNode stepArray(ExecutedStep... steps) {
        ArrayNode array = om.createArrayNode();
        for (ExecutedStep step : steps) {
            ObjectNode node = array.addObject();
            node.put("tool", step.tool());
            node.put("success", !isToolError(step.result()));
            node.put("elapsed_ms", step.elapsedMs());
            copyScalars(node, step.result(), "document_id", "file_id", "revision", "path", "total_count");
        }
        return array;
    }

    private boolean writesData(String functionName) {
        SkillFunction fn = skillCatalog.getFunction(functionName);
        return fn != null && fn.safety() != null && fn.safety().writesData();
    }

    private ObjectNode withoutCompose(JsonNode arguments) {
        ObjectNode out = om.createObjectNode();
        if (arguments == null || !arguments.isObject()) {
            return out;
        }
        arguments.fields().forEachRemaining(entry -> {
            if (!"compose".equals(entry.getKey())) {
                out.set(entry.getKey(), entry.getValue().deepCopy());
            }
        });
        return out;
    }

    private void copyIfPresent(ObjectNode target, JsonNode source, String field) {
        if (source != null && source.has(field)) {
            target.set(field, source.get(field).deepCopy());
        }
    }

    private void copyTextSlot(ObjectNode target, JsonNode source, String sourceField, String targetField) {
        String value = source.path(sourceField).asText("").trim();
        if (!value.isBlank()) {
            target.put(targetField, value);
        }
    }

    private void copyScalars(ObjectNode target, JsonNode source, String... fields) {
        if (target == null || source == null || !source.isObject()) {
            return;
        }
        for (String field : fields) {
            JsonNode value = source.get(field);
            if (value != null && (value.isTextual() || value.isNumber() || value.isBoolean())) {
                target.set(field, value.deepCopy());
            }
        }
    }

    private long firstPositiveLong(JsonNode primary, JsonNode fallback, String field) {
        long value = primary == null ? 0L : primary.path(field).asLong(0L);
        if (value > 0) {
            return value;
        }
        return fallback == null ? 0L : Math.max(fallback.path(field).asLong(0L), 0L);
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

    private static String normalize(String value) {
        return Objects.toString(value, "").trim();
    }

    private record ExecutedStep(String tool, JsonNode arguments, JsonNode result, long elapsedMs) {
    }

    private record AssertionOutcome(boolean ok, String reason, ObjectNode slots) {
    }
}
