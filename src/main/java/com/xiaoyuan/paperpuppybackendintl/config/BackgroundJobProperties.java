package com.xiaoyuan.paperpuppybackendintl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.background-jobs")
public class BackgroundJobProperties {

    private boolean enabled = true;

    private int batchSize = 10;

    private int maxAttempts = 5;

    private long pollDelayMs = 5_000;

    private long recoverDelayMs = 60_000;

    private long initialRetryDelaySeconds = 30;

    private long maxRetryDelaySeconds = 3_600;

    private long staleRunningSeconds = 900;
}
