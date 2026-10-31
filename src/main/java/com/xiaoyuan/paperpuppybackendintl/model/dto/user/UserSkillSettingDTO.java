package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户技能配置 DTO（融合 skill_registry + user_skill_settings）
 */
@Data
public class UserSkillSettingDTO {

    /** skill_registry.skill_name */
    private String skillName;

    /** 前端展示名称 */
    private String displayName;

    /** 技能描述 */
    private String description;

    /** 分类：system | file | document | research | user_management */
    private String category;

    /** 是否系统锁定（不可关闭） */
    private Boolean systemLocked;

    /** 当前生效状态（已合并用户设置和默认值） */
    private Boolean enabled;

    /** 前端排序 */
    private Integer displayOrder;

    /** 前端图标标识 */
    private String icon;

    /** 用户最后修改时间（null 表示用户未修改过，使用默认值） */
    private OffsetDateTime userSettingUpdatedAt;
}
