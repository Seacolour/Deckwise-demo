package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class UserMemoryDTO {
    private Long id;
    private String title;
    private String content;
    private String source;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
