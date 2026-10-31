package com.xiaoyuan.paperpuppybackendintl.model.entity;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class Session {
    private Long id;
    private Long projectId;
    private Long createdBy;
    private String title;
    private String lastUserMessage;
    private OffsetDateTime lastActiveAt;
    private Boolean archived;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Boolean deleted;
}


