package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 统一技能视图 DTO：将系统 Function Tool 和用户自定义 PromptSkill 合并为同一结构。
 * 前端可根据 {@code type} 字段区分展示逻辑和操作入口。
 */
@Data
public class UnifiedSkillDTO {

    /**
     * 技能类型：
     * <ul>
     *   <li>{@code system_tool} — 系统内置 Function Tool（来自 skill_registry）</li>
     *   <li>{@code prompt_skill} — 用户自定义 PromptSkill（来自 user_custom_skills）</li>
     * </ul>
     */
    private String type;

    private String skillName;

    private String displayName;

    private String description;

    /** 分类：system | file | document | research | user_management | custom */
    private String category;

    private Boolean enabled;

    /** 仅 system_tool：是否系统锁定（不可关闭） */
    private Boolean systemLocked;

    /** 仅 system_tool：前端排序 */
    private Integer displayOrder;

    /** 仅 system_tool：前端图标标识 */
    private String icon;

    /** 仅 prompt_skill：user_custom_skills.id（用于更新/删除/开关操作） */
    private Long customSkillId;

    /** 仅 prompt_skill：导入来源 manual | file | zip */
    private String sourceType;

    /** 仅 prompt_skill：版本号 */
    private String version;

    private OffsetDateTime updatedAt;
}
