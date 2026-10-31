package com.xiaoyuan.paperpuppybackendintl.model.entity;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户自定义 PromptSkill 实体（对应 user_custom_skills 表）
 */
@Data
public class UserCustomSkill {

    private Long id;

    private Long userId;

    /** SKILL.md frontmatter 中的 name */
    private String skillName;

    private String displayName;

    private String description;

    /** Markdown body（注入 AI 的指令内容） */
    private String content;

    private String version;

    /** 导入来源：manual | file | zip */
    private String sourceType;

    private Boolean enabled;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private Boolean deleted;
}
