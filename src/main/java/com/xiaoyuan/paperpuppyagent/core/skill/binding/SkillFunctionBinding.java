package com.xiaoyuan.paperpuppyagent.core.skill.binding;

import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;

/**
 * 单个 function 的运行时绑定。
 */
public record SkillFunctionBinding(
        String functionName,
        SkillRuntimeType runtimeType,
        String toolName,
        String operation,
        String action
) {
    public boolean isVisibleToModel() {
        return true;
    }

    public boolean isExecutionAllowed(ToolExecutionContext ctx) {
        return true;
    }
}
