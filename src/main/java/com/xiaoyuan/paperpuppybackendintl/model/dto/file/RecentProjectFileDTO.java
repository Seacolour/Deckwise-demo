package com.xiaoyuan.paperpuppybackendintl.model.dto.file;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecentProjectFileDTO {
    private Long fileId;
    private String cacheKey;
    private String fileName;
    private String mimeType;
    private String fileExt;
    private Long sizeBytes;
    private String objectKey;
    private String publicUrl;
    private String storageChannel;
    private String visibility;
    private OffsetDateTime createdAt;
}
