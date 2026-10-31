package com.xiaoyuan.paperpuppybackendintl.model.dto.file;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class PendingProjectFileUploadResponse {
    private String pendingFileRef;
    private String fileName;
    private Long fileSize;
    private Long sizeBytes;
    private String mimeType;
    private OffsetDateTime expiresAt;
}
