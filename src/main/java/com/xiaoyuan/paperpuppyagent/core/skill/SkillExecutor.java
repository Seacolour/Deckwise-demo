package com.xiaoyuan.paperpuppyagent.core.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.skill.binding.SkillBindingCatalog;
import com.xiaoyuan.paperpuppyagent.core.skill.binding.SkillFunctionBinding;
import com.xiaoyuan.paperpuppyagent.core.skill.runtime.SkillExecutionResult;
import com.xiaoyuan.paperpuppyagent.core.skill.runtime.SkillRuntimeRouter;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolErrorSupport;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * SkillExecutor：根据 Skill manifest + binding 做最小 scope 校验，并委托 runtime 执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillExecutor {

    private final ObjectMapper om;
    private final SkillCatalog catalog;
    private final SkillBindingCatalog bindingCatalog;
    private final SkillRuntimeRouter runtimeRouter;

    public SkillExecutionResult execute(String functionName, JsonNode arguments, ToolExecutionContext ctx) {
        SkillFunction fn = catalog.getFunction(functionName);
        if (fn == null) {
            return SkillExecutionResult.toolResult(error(functionName, "UNKNOWN_FUNCTION", "Function not found: " + functionName));
        }
        if (catalog.isGloballyDisabled(functionName)) {
            return SkillExecutionResult.toolResult(
                    error(functionName, "CAPABILITY_DISABLED", "Capability is globally disabled for: " + functionName));
        }
        // scope 校验：只做最小保护，避免明显的上下文缺失导致越权/空跑
        SkillScope scope = fn.scope() == null ? SkillScope.none() : fn.scope();
        if (scope.requiresUser() && (ctx == null || ctx.userId() <= 0)) {
            return SkillExecutionResult.toolResult(error(functionName, "INVALID_CONTEXT", "missing/invalid userId for " + functionName));
        }
        if (scope.requiresProject() && (ctx == null || ctx.projectId() <= 0)) {
            return SkillExecutionResult.toolResult(error(functionName, "INVALID_CONTEXT", "missing/invalid projectId for " + functionName));
        }
        if (scope.requiresSession() && (ctx == null || ctx.sessionId() <= 0)) {
            return SkillExecutionResult.toolResult(error(functionName, "INVALID_CONTEXT", "missing/invalid sessionId for " + functionName));
        }
        SkillFunctionBinding binding = bindingCatalog.getBinding(functionName);
        if (binding == null) {
            return SkillExecutionResult.toolResult(error(functionName, "MISSING_BINDING", "Skill binding not found for: " + functionName));
        }
        if (!binding.isExecutionAllowed(ctx)) {
            return SkillExecutionResult.toolResult(
                    error(functionName, "CAPABILITY_NOT_ALLOWED", "Capability not allowed for: " + functionName));
        }

        long startNs = System.nanoTime();
        try {
            SkillExecutionResult result = runtimeRouter.execute(
                    fn,
                    binding,
                    arguments,
                    Objects.requireNonNullElseGet(ctx, ToolExecutionContext::empty));
            long costMs = (System.nanoTime() - startNs) / 1_000_000;
            JsonNode payload = result == null ? om.createObjectNode() : result.payload();
            log.info("[SkillExecutor] executed: fn={}, runtime={}, costMs={}, ok={}",
                    functionName, binding.runtimeType(), costMs, summarizeOk(payload));
            if (result == null) {
                return SkillExecutionResult.toolResult(om.createObjectNode());
            }
            if (result.payload() == null) {
                return SkillExecutionResult.toolResult(om.createObjectNode());
            }
            return result;
        } catch (Exception e) {
            long costMs = (System.nanoTime() - startNs) / 1_000_000;
            log.warn("[SkillExecutor] failed: fn={}, runtime={}, costMs={}, err={}",
                    functionName, binding.runtimeType(), costMs, e.getMessage());
            return SkillExecutionResult.toolResult(ToolErrorSupport.fromException(om, functionName, e));
        }
    }

    private ObjectNode error(String functionName, String code, String message) {
        return ToolErrorSupport.businessError(
                om,
                functionName,
                code,
                message,
                "请检查当前工具调用所需的上下文、配置或权限条件是否满足。",
                false
        );
    }

    private static boolean summarizeOk(JsonNode n) {
        if (n == null) return false;
        JsonNode s = n.get("success");
        return s != null && s.isBoolean() && s.asBoolean(false);
    }
}

