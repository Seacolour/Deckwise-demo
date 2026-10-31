package com.xiaoyuan.paperpuppybackendintl.model.dto.file;

import lombok.Data;

@Data
public class PrivateObjectAccessUrlResponse {
    private Long fileId;
    private String objectKey;
    private String accessUrl;
    private String scope;
    private String storageChannel;
    private String visibility;
    private long ttlSeconds;
}
