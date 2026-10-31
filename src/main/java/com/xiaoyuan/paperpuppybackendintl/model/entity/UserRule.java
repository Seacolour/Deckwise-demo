package com.xiaoyuan.paperpuppybackendintl.model.entity;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户自定义规则实体（对应 user_rules 表）
 */
@Data
public class UserRule {

    private Long id;

    private Long userId;

    private String content;

    private Boolean enabled;

    /** 来源：'user'(手动) / 'ai'(AI建议) */
    private String source;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private Boolean deleted;
}
