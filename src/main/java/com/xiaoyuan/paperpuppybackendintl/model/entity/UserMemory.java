package com.xiaoyuan.paperpuppybackendintl.model.entity;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户记忆实体（对应 user_memories 表）
 */
@Data
public class UserMemory {

    private Long id;

    private Long userId;

    private String title;

    private String content;

    /** 来源：'ai'(自动提取) / 'user'(手动) */
    private String source;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private Boolean deleted;
}
