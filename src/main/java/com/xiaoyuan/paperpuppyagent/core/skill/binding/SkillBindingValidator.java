package com.xiaoyuan.paperpuppyagent.core.skill.binding;

import com.xiaoyuan.paperpuppyagent.core.skill.SkillCatalog;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 对 skill binding 做启动期 fail-fast 校验，避免声明层与执行层漂移。
 */
@Component
@RequiredArgsConstructor
public class SkillBindingValidator {
    private final SkillCatalog skillCatalog;

    public SkillFunctionBinding validateFunctionBinding(SkillFunctionBinding rawBinding, String source) {
        if (rawBinding == null) {
            throw new IllegalArgumentException("invalid skill.binding.json (null binding): " + source);
        }
        String functionName = text(rawBinding.functionName());
        if (functionName.isBlank()) {
            throw new IllegalArgumentException("invalid skill.binding.json (blank function_name): " + source);
        }
        SkillFunction function = skillCatalog.getFunction(functionName);
        if (function == null) {
            throw new IllegalStateException("binding function not found in skill catalog: " + functionName + " source=" + source);
        }
        SkillRuntimeType runtimeType = Objects.requireNonNull(rawBinding.runtimeType(),
                "binding runtimeType cannot be null: " + functionName + " source=" + source);
        return switch (runtimeType) {
            case NATIVE -> validateNative(functionName, rawBinding);
        };
    }

    public void validateCoverage(Map<String, SkillFunctionBinding> bindingsByFunction) {
        List<String> missingBindings = new ArrayList<>();
        for (SkillFunction function : skillCatalog.listFunctions()) {
            if (!bindingsByFunction.containsKey(function.name())) {
                missingBindings.add(function.name());
            }
        }
        if (!missingBindings.isEmpty()) {
            missingBindings.sort(String::compareTo);
            throw new IllegalStateException("missing skill bindings for functions: " + missingBindings);
        }
    }

    private static SkillFunctionBinding validateNative(String functionName, SkillFunctionBinding rawBinding) {
        String toolName = text(rawBinding.toolName());
        return new SkillFunctionBinding(
                functionName,
                SkillRuntimeType.NATIVE,
                toolName.isBlank() ? functionName : toolName,
                null,
                null);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
