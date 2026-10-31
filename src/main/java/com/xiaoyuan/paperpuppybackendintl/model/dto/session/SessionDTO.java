package com.xiaoyuan.paperpuppybackendintl.model.dto.session;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class SessionDTO {
    private Long id;
    private Long projectId;
    private Long createdBy;
    private String title;
    private String lastUserMessage;
    private OffsetDateTime lastActiveAt;
    private Boolean archived;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}


