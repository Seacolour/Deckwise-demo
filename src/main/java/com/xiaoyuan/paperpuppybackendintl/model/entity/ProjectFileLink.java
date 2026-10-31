package com.xiaoyuan.paperpuppybackendintl.model.entity;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ProjectFileLink {
    private Long id;
    private Long projectId;
    private Long fileId;
    private Long addedBy;
    private String displayName;
    private String kind;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Boolean deleted;
}


