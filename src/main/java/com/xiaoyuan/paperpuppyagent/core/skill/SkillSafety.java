package com.xiaoyuan.paperpuppyagent.core.skill;

/**
 * Skill 的安全属性声明（当前主要用于描述，不做强制隔离）。
 */
public record SkillSafety(
        boolean writesData,
        boolean idempotent
) {
    public static SkillSafety defaultReadOnly() {
        return new SkillSafety(false, true);
    }
}

