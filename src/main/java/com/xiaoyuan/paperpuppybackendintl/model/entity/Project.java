package com.xiaoyuan.paperpuppybackendintl.model.entity;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 项目（工作空间）
 */
@Data
public class Project {
    private Long id;
    private Long ownerId;
    private String name;
    private String description;
    private String coverImage;
    private List<String> tags;
    private Boolean archived;
    private OffsetDateTime lastActiveAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Boolean deleted;
}


