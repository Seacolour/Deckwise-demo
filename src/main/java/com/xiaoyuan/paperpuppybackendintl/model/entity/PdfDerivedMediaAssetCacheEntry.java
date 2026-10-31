package com.xiaoyuan.paperpuppybackendintl.model.entity;

import lombok.Data;
import org.jooq.JSONB;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class PdfDerivedMediaAssetCacheEntry {

    private Long id;
    private Long ownerUserId;
    private String sourceContentHash;
    private Long sourceFileId;
    private String extractorSignature;
    private Long derivedFileId;

    private String kind;
    private String provider;
    private Integer pageNumber;
    private Integer assetIndex;

    private JSONB bbox;
    private String caption;
    private String description;
    private String contentHash;
    private BigDecimal score;

    private String status;
    private String errorMessage;
    private JSONB metadata;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Boolean deleted;

    private String derivedFileName;
    private String derivedMimeType;
    private String derivedFileExt;
    private Long derivedSizeBytes;
    private String derivedContentHash;
    private String derivedPublicDomain;
    private String derivedPublicPath;
    private String derivedObjectKey;
}
