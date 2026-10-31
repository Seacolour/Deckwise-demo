package com.xiaoyuan.paperpuppybackendintl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.project-gc")
public class ProjectGarbageCollectionProperties {

    private long scannerDelayMs = 1_800_000;

    private boolean emptyProjectShellScannerEnabled = true;

    private int emptyProjectShellScannerBatchSize = 50;

    private long emptyProjectShellCandidateMinAgeSeconds = 86_400;

    private long emptyProjectShellMaxCreatedUpdatedDeltaSeconds = 300;
}
