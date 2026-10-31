package com.xiaoyuan.paperpuppybackendintl.model.entity;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class DocumentVersion {
    private Long id;
    private Long documentId;
    private Integer versionNo;
    private Long fileId;
    private String message;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private Boolean deleted;
}

