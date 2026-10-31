package com.xiaoyuan.paperpuppybackendintl.service.storage;

public record ObjectStorageFileWriteRequest(
        Long ownerUserId,
        String sourceType,
        String sourceKey,
        String originalName,
        String contentType,
        String fileExt,
        Long sizeBytes,
        byte[] bytes,
        ObjectStorageWriteOptions options) {
}
