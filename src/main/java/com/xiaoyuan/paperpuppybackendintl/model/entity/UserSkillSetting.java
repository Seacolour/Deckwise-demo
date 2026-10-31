package com.xiaoyuan.paperpuppybackendintl.model.entity;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户技能开关设置实体（对应 user_skill_settings 表）
 */
@Data
public class UserSkillSetting {

    private Long id;

    private Long userId;

    /** skill.json 中 functions[].name */
    private String skillName;

    private Boolean enabled;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private Boolean deleted;
}
