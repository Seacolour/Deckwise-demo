package com.xiaoyuan.paperpuppyagent.core.skill.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillFunction;
import com.xiaoyuan.paperpuppyagent.core.skill.binding.SkillFunctionBinding;
import com.xiaoyuan.paperpuppyagent.core.skill.binding.SkillRuntimeType;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;

/**
 * skill 运行时接口，由不同 runtime 承担不同执行语义。
 */
public interface SkillRuntime {
    SkillRuntimeType type();

    SkillExecutionResult execute(SkillFunction function,
                                 SkillFunctionBinding binding,
                                 JsonNode arguments,
                                 ToolExecutionContext context) throws Exception;
}
