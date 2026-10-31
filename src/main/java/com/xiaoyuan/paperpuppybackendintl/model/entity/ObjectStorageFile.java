package com.xiaoyuan.paperpuppybackendintl.model.entity;

import lombok.Data;
import org.jooq.JSONB;

import java.time.OffsetDateTime;

@Data
public class ObjectStorageFile {
    private Long id;

    private Long ownerUserId;

    private String sourceType;
    private String sourceKey;

    private String storageType;
    private String storageChannel;
    private String visibility;
    private String bucket;
    private String objectKey;

    private String publicDomain;
    private String publicPath;

    private String mimeType;
    private String fileExt;
    private Long sizeBytes;
    private String originalName;

    private String etag;
    private String versionId;
    private OffsetDateTime lastModified;

    private String status;
    private String sourceId;

    private JSONB metadata;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Boolean deleted;
}


