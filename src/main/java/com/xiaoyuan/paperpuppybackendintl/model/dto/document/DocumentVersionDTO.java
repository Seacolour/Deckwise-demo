package com.xiaoyuan.paperpuppybackendintl.model.dto.document;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class DocumentVersionDTO {
    private Long id;
    private Long documentId;
    private Integer versionNo;
    private Long fileId;
    private String message;
    private Long createdBy;
    private OffsetDateTime createdAt;
}

