package com.xiaoyuan.paperpuppybackendintl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.file-gc")
public class FileGarbageCollectionProperties {

    private boolean orphanScannerEnabled = true;

    private long orphanScannerDelayMs = 300_000;

    private int orphanScannerBatchSize = 100;

    private long orphanCandidateMinAgeSeconds = 86_400;

    private long projectDeleteGraceSeconds = 86_400;

    private long derivedCacheRetentionSeconds = 604_800;
}
