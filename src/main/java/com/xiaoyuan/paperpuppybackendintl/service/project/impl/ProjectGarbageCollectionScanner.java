package com.xiaoyuan.paperpuppybackendintl.service.project.impl;

import com.xiaoyuan.paperpuppybackendintl.annotation.DistributedScheduled;
import com.xiaoyuan.paperpuppybackendintl.config.ProjectGarbageCollectionProperties;
import com.xiaoyuan.paperpuppybackendintl.service.project.ProjectGarbageCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectGarbageCollectionScanner {

    private final ProjectGarbageCollectionProperties properties;
    private final ProjectGarbageCollectionService projectGarbageCollectionService;

    @Scheduled(fixedDelayString = "${app.project-gc.scanner-delay-ms:1800000}")
    @DistributedScheduled(lockKey = "project-gc:empty-project-shell-scanner", waitTime = 1, leaseTime = 300)
    public void scanEmptyProjectShells() {
        if (!properties.isEmptyProjectShellScannerEnabled()) {
            return;
        }
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC)
                .minusSeconds(safeEmptyProjectShellMinAgeSeconds());
        int deleted = projectGarbageCollectionService.collectEmptyProjectShells(
                safeEmptyProjectShellBatchSize(),
                cutoff,
                safeEmptyProjectShellMaxCreatedUpdatedDeltaSeconds());
        if (deleted > 0) {
            log.info("[ProjectGCScanner] deleted empty project shells: count={}, cutoff={}",
                    deleted,
                    cutoff);
        }
    }

    private int safeEmptyProjectShellBatchSize() {
        return Math.max(1, Math.min(properties.getEmptyProjectShellScannerBatchSize(), 200));
    }

    private long safeEmptyProjectShellMinAgeSeconds() {
        return Math.max(0, properties.getEmptyProjectShellCandidateMinAgeSeconds());
    }

    private long safeEmptyProjectShellMaxCreatedUpdatedDeltaSeconds() {
        return Math.max(0, properties.getEmptyProjectShellMaxCreatedUpdatedDeltaSeconds());
    }

}
