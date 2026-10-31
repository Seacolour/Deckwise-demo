package com.xiaoyuan.paperpuppybackendintl.service.file.impl;

import com.xiaoyuan.paperpuppybackendintl.annotation.DistributedScheduled;
import com.xiaoyuan.paperpuppybackendintl.config.FileGarbageCollectionProperties;
import com.xiaoyuan.paperpuppybackendintl.service.file.FileGarbageCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileGarbageCollectionScanner {

    private final FileGarbageCollectionProperties properties;
    private final FileGarbageCollectionService fileGarbageCollectionService;

    @Scheduled(fixedDelayString = "${app.file-gc.orphan-scanner-delay-ms:1800000}")
    @DistributedScheduled(lockKey = "file-gc:orphan-scanner", waitTime = 1, leaseTime = 300)
    public void scanOrphanFiles() {
        if (!properties.isOrphanScannerEnabled()) {
            return;
        }
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC)
                .minusSeconds(safeMinAgeSeconds());
        int enqueued = fileGarbageCollectionService.enqueueOrphanCandidates(
                safeBatchSize(),
                cutoff);
        if (enqueued > 0) {
            log.info("[FileGCScanner] enqueued orphan file GC candidates: count={}, cutoff={}",
                    enqueued,
                    cutoff);
        }
    }

    private int safeBatchSize() {
        return Math.max(1, Math.min(properties.getOrphanScannerBatchSize(), 500));
    }

    private long safeMinAgeSeconds() {
        return Math.max(0, properties.getOrphanCandidateMinAgeSeconds());
    }
}
