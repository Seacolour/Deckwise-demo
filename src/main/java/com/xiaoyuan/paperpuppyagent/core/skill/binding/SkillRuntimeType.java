package com.xiaoyuan.paperpuppyagent.core.skill.binding;

import java.util.Locale;

/**
 * skill binding 对应的执行 runtime 类型。
 */
public enum SkillRuntimeType {
    NATIVE;

    public static SkillRuntimeType fromText(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("runtime 不能为空");
        }
        return SkillRuntimeType.valueOf(text.toUpperCase(Locale.ROOT));
    }
}
