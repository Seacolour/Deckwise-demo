package com.xiaoyuan.paperpuppybackendintl.model.entity;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 技能注册表实体（对应 skill_registry 表）
 */
@Data
public class SkillRegistryItem {

    private Long id;

    /** 唯一标识，对应 skill.json functions[].name */
    private String skillName;

    /** 前端展示名称 */
    private String displayName;

    private String description;

    /** 分类：system | file | document | research | user_management | general */
    private String category;

    /** true = 系统核心工具，用户不可关闭 */
    private Boolean systemLocked;

    /** 新用户的默认启用状态 */
    private Boolean defaultEnabled;

    /** 前端排序 */
    private Integer displayOrder;

    /** 前端图标标识 */
    private String icon;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private Boolean deleted;
}
