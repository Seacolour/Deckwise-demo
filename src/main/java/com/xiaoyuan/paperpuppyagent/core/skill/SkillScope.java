package com.xiaoyuan.paperpuppyagent.core.skill;

/**
 * Skill 的作用域/上下文需求声明（用于最小化权限与提示词注入风险）。
 */
public record SkillScope(
        boolean requiresUser,
        boolean requiresProject,
        boolean requiresSession
) {
    public static SkillScope none() {
        return new SkillScope(false, false, false);
    }
}

