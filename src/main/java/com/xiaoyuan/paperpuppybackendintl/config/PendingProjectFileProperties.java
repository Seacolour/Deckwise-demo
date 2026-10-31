package com.xiaoyuan.paperpuppybackendintl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "storage.r2.pending-project-file")
public class PendingProjectFileProperties {

    /**
     * Pending upload can be finalized before this TTL expires. Defaults to 24 hours.
     */
    private long metadataTtlSeconds = 24 * 60 * 60;

    /**
     * Keep Redis metadata after logical expiry so the cleanup worker can still find the R2 key.
     */
    private long cleanupGraceSeconds = 7 * 24 * 60 * 60;

    /**
     * Scheduled cleanup cadence.
     */
    private long cleanupDelayMs = 10 * 60 * 1000;

    /**
     * Maximum pending files reclaimed per cleanup pass.
     */
    private int cleanupBatchSize = 100;
}
