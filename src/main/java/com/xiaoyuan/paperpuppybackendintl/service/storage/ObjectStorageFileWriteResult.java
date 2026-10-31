package com.xiaoyuan.paperpuppybackendintl.service.storage;

import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;

public record ObjectStorageFileWriteResult(
        ObjectStorageFile stored,
        String publicUrl,
        boolean newlyCreated) {
}
