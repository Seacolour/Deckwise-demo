package com.xiaoyuan.paperpuppybackendintl.model.dto.file;

import java.util.Map;

public record FileGarbageCollectionResult(
        Long ownerUserId,
        Long fileId,
        String reason,
        String status,
        boolean purged,
        int scheduledObjectDeleteCount,
        Map<String, Long> blockers) {
}
