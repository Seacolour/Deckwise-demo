package com.xiaoyuan.paperpuppyagent.core.skill.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillFunction;
import com.xiaoyuan.paperpuppyagent.core.skill.binding.SkillFunctionBinding;
import com.xiaoyuan.paperpuppyagent.core.skill.binding.SkillRuntimeType;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 binding.runtime 将 function 分发到不同执行 runtime。
 */
@Component
public class SkillRuntimeRouter {
    private final Map<SkillRuntimeType, SkillRuntime> runtimes = new EnumMap<>(SkillRuntimeType.class);

    public SkillRuntimeRouter(List<SkillRuntime> runtimeList) {
        if (runtimeList != null) {
            for (SkillRuntime runtime : runtimeList) {
                runtimes.put(runtime.type(), runtime);
            }
        }
    }

    public SkillExecutionResult execute(SkillFunction function,
                                        SkillFunctionBinding binding,
                                        JsonNode arguments,
                                        ToolExecutionContext context) throws Exception {
        SkillRuntime runtime = runtimes.get(binding.runtimeType());
        if (runtime == null) {
            throw new IllegalStateException("Skill runtime not registered: " + binding.runtimeType());
        }
        return runtime.execute(function, binding, arguments, context);
    }
}
