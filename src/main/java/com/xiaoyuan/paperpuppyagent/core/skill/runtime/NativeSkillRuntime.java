package com.xiaoyuan.paperpuppyagent.core.skill.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillFunction;
import com.xiaoyuan.paperpuppyagent.core.skill.binding.SkillFunctionBinding;
import com.xiaoyuan.paperpuppyagent.core.skill.binding.SkillRuntimeType;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Native runtime：继续复用当前本地 ToolRegistry。
 */
@Component
@RequiredArgsConstructor
public class NativeSkillRuntime implements SkillRuntime {
    private final ToolRegistry toolRegistry;

    @Override
    public SkillRuntimeType type() {
        return SkillRuntimeType.NATIVE;
    }

    @Override
    public SkillExecutionResult execute(SkillFunction function,
                                        SkillFunctionBinding binding,
                                        JsonNode arguments,
                                        ToolExecutionContext context) throws Exception {
        String toolName = binding.toolName();
        Tool tool = toolRegistry.getToolByName(toolName);
        if (tool == null) {
            throw new IllegalStateException("Native tool implementation not found for: " + toolName);
        }
        return SkillExecutionResult.toolResult(tool.execute(arguments, context));
    }
}
