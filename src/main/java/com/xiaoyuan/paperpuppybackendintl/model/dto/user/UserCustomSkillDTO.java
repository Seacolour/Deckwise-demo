package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户自定义 PromptSkill 响应 DTO
 */
@Data
public class UserCustomSkillDTO {

    private Long id;

    private String skillName;

    private String displayName;

    private String description;

    /** Markdown body（前端预览/编辑用） */
    private String content;

    private String version;

    /** 导入来源：manual | file | zip */
    private String sourceType;

    private Boolean enabled;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
