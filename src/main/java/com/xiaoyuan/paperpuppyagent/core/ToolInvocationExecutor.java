package com.xiaoyuan.paperpuppyagent.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.xiaoyuan.paperpuppyagent.config.AgentModelCallProperties;
import com.xiaoyuan.paperpuppyagent.core.composition.ToolCompositionRuntime;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillCatalog;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillExecutor;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillFunction;
import com.xiaoyuan.paperpuppyagent.core.skill.runtime.SkillExecutionResult;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolErrorSupport;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

/** Executes one tool invocation, including argument validation and write guards. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolInvocationExecutor {
    private final ObjectMapper om;
    private final AgentModelCallProperties modelCallProperties;
    private final SkillCatalog skillCatalog;
    private final SkillExecutor skillExecutor;
    private ToolCompositionRuntime toolCompositionRuntime;

    @Autowired(required = false)
    public void setToolCompositionRuntime(ToolCompositionRuntime toolCompositionRuntime) {
        this.toolCompositionRuntime = toolCompositionRuntime;
    }

    public boolean writesData(String functionName) {
        SkillFunction fn = skillCatalog.getFunction(functionName);
        return fn != null && fn.safety() != null && fn.safety().writesData();
    }

    public InvocationResult execute(ResponseFunctionToolCall call,
                                    ToolExecutionContext toolContext,
                                    int batchSize,
                                    int writeOrdinal) {
        String name = Objects.toString(call == null ? null : call.name(), "").trim();
        String callId = Objects.toString(call == null ? null : call.callId(), "").trim();
        String argsStr = Objects.toString(call == null ? null : call.arguments(), "");
        log.info("[Agent][Tool] 收到 tool_call: callId={}, name={}, args={}",
                callId, name, truncate(argsStr, 800));

        ParsedArgs parsedArgs = parseArgsJson(name, argsStr);
        JsonNode args = parsedArgs.args();
        Instant toolStartedAt = Instant.now();
        long toolStartNs = System.nanoTime();
        JsonNode result;

        boolean blockParallelWrite = modelCallProperties.isGuardParallelWrites()
                && batchSize > 1
                && writeOrdinal > 1;
        if (blockParallelWrite) {
            ObjectNode err = ToolErrorSupport.businessError(
                    om,
                    name,
                    "SEQUENTIAL_WRITE_REQUIRED",
                    "Multiple write tools were emitted in one model response. Only the first write tool is executed.",
                    "请在下一轮使用最新 revision 继续后续写入；读取类工具可以并行，写入类工具必须按返回的新 revision 顺序提交。",
                    false);
            err.put("parallel_write_blocked", true);
            err.put("call_id", callId);
            result = err;
            log.warn("[Agent][Tool] 已阻止同轮并行写工具: callId={}, name={}, costMs={}",
                    callId, name, elapsedMs(toolStartNs));
        } else if (parsedArgs.error() != null) {
            result = parsedArgs.error();
            log.warn("[Agent][Tool] 工具参数 JSON 解析失败: callId={}, name={}, costMs={}, argsChars={}, error={}",
                    callId, name, elapsedMs(toolStartNs), argsStr.length(), parsedArgs.parseError());
        } else {
            result = executeRuntime(name, callId, args, toolContext, toolStartNs);
        }

        long costMs = elapsedMs(toolStartNs);
        Instant toolFinishedAt = Instant.now();
        boolean error = isToolError(result);
        return new InvocationResult(
                name,
                callId,
                argsStr,
                args,
                result == null ? om.createObjectNode() : result,
                costMs,
                toolStartedAt,
                toolFinishedAt,
                error,
                summarizeErrorMessage(result),
                summarizeErrorCode(result),
                summarizeErrorHint(result));
    }

    private JsonNode executeRuntime(String name,
                                    String callId,
                                    JsonNode args,
                                    ToolExecutionContext toolContext,
                                    long toolStartNs) {
        try {
            log.info("[Agent][Tool] 开始执行工具: callId={}, name={}", callId, name);
            SkillExecutionResult executionResult = toolCompositionRuntime != null && toolCompositionRuntime.hasCompose(args)
                    ? toolCompositionRuntime.execute(name, args, withCallId(toolContext, callId))
                    : skillExecutor.execute(name, args, withCallId(toolContext, callId));
            JsonNode payload = executionResult == null ? null : executionResult.payload();
            if (isToolError(payload)) {
                log.warn("[Agent][Tool] 工具执行完成(失败): callId={}, name={}, costMs={}, errSummary={}",
                        callId, name, elapsedMs(toolStartNs), Objects.toString(summarizeErrorMessage(payload), ""));
            } else {
                log.info("[Agent][Tool] 工具执行完成: callId={}, name={}, costMs={}, resultSummary={}",
                        callId, name, elapsedMs(toolStartNs), summarizeToolResult(payload));
            }
            return payload;
        } catch (Exception e) {
            log.error("[Agent][Tool] 工具执行失败: callId={}, name={}, costMs={}, error={}",
                    callId, name, elapsedMs(toolStartNs), e.getMessage(), e);
            return ToolErrorSupport.fromException(om, name, e);
        }
    }

    private static ToolExecutionContext withCallId(ToolExecutionContext context, String callId) {
        return (context == null ? ToolExecutionContext.empty() : context).withToolCallId(callId);
    }

    private ParsedArgs parseArgsJson(String functionName, String argsStr) {
        String s = Objects.toString(argsStr, "").trim();
        if (s.isBlank()) {
            return new ParsedArgs(om.createObjectNode(), null, "");
        }
        try {
            return new ParsedArgs(om.readTree(s), null, "");
        } catch (Exception e) {
            ObjectNode err = ToolErrorSupport.businessError(
                    om,
                    functionName,
                    "INVALID_TOOL_ARGUMENTS",
                    "Tool arguments are not valid JSON. The function call was likely truncated before a complete JSON object was emitted.",
                    invalidArgumentsHint(functionName),
                    false);
            err.put("arguments_chars", s.length());
            err.put("arguments_preview", truncate(s, 800));
            err.put("parse_error", Objects.toString(e.getMessage(), "invalid json"));
            return new ParsedArgs(om.createObjectNode(), err, e.getMessage());
        }
    }

    private static String invalidArgumentsHint(String functionName) {
        return "请用完整、可解析的 JSON 重新调用工具；如果上一轮输出接近 max_output_tokens，请减少本次参数体积后再试。";
    }

    private String summarizeToolResult(JsonNode result) {
        if (result == null) {
            return "null";
        }
        try {
            return truncate(om.writeValueAsString(result), 300);
        } catch (Exception e) {
            return "unserializable(" + e.getMessage() + ")";
        }
    }

    static boolean isToolError(JsonNode result) {
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

    private static String summarizeErrorMessage(JsonNode result) {
        if (result == null || !result.isObject()) {
            return null;
        }
        String message = text(result.get("message"), 200);
        return message == null ? text(result.get("error"), 200) : message;
    }

    private static String summarizeErrorCode(JsonNode result) {
        return result == null || !result.isObject() ? null : text(result.get("error_code"), 120);
    }

    private static String summarizeErrorHint(JsonNode result) {
        return result == null || !result.isObject() ? null : text(result.get("hint"), 200);
    }

    private static String text(JsonNode node, int maxChars) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asText("").trim();
        if (value.isBlank()) {
            return null;
        }
        return value.length() > maxChars ? value.substring(0, maxChars) + "..." : value;
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000;
    }

    private static String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() <= maxChars
                ? text
                : text.substring(0, maxChars) + "...(truncated,len=" + text.length() + ")";
    }

    private record ParsedArgs(JsonNode args, ObjectNode error, String parseError) {
    }

    public record InvocationResult(
            String name,
            String callId,
            String argumentsJson,
            JsonNode arguments,
            JsonNode result,
            long costMs,
            Instant startedAt,
            Instant finishedAt,
            boolean error,
            String errorMessage,
            String errorCode,
            String errorHint) {
    }
}
