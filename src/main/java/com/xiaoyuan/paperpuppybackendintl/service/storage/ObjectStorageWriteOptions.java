package com.xiaoyuan.paperpuppybackendintl.service.storage;

import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageChannel;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageVisibility;

public record ObjectStorageWriteOptions(
        StorageChannel channel,
        StorageVisibility visibility,
        String scopePrefix) {

    public static ObjectStorageWriteOptions publicObject(String scopePrefix) {
        return new ObjectStorageWriteOptions(StorageChannel.PUBLIC, StorageVisibility.PUBLIC, scopePrefix);
    }

    public static ObjectStorageWriteOptions privateObject(String scopePrefix) {
        return new ObjectStorageWriteOptions(StorageChannel.PRIVATE, StorageVisibility.PRIVATE, scopePrefix);
    }

    public StorageChannel effectiveChannel() {
        return channel == null ? StorageChannel.PUBLIC : channel;
    }

    public StorageVisibility effectiveVisibility() {
        if (visibility != null) {
            return visibility;
        }
        return effectiveChannel() == StorageChannel.PRIVATE
                ? StorageVisibility.PRIVATE
                : StorageVisibility.PUBLIC;
    }
}
