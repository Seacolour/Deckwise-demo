package com.xiaoyuan.paperpuppybackendintl.service.skill;

/**
 * skill.md 解析结果：frontmatter 元数据 + Markdown body
 */
public record ParsedSkill(
        String name,
        String displayName,
        String description,
        String version,
        /** Markdown body（frontmatter 之后的全部内容） */
        String content
) {
}
