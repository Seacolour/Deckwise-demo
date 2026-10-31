package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class UserRuleDTO {
    private Long id;
    private String content;
    private Boolean enabled;
    private String source;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
