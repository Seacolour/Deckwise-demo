package com.xiaoyuan.paperpuppyagent.core.skill.runtime;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * 统一封装 skill runtime 的执行结果。
 */
public record SkillExecutionResult(
        ResultKind kind,
        JsonNode payload
) {
    public enum ResultKind {
        TOOL_RESULT
    }

    public static SkillExecutionResult toolResult(JsonNode payload) {
        return new SkillExecutionResult(ResultKind.TOOL_RESULT, payload);
    }

    public boolean isToolResult() {
        return kind == ResultKind.TOOL_RESULT;
    }

    public SkillExecutionResult {
        Objects.requireNonNull(kind, "kind");
    }
}
