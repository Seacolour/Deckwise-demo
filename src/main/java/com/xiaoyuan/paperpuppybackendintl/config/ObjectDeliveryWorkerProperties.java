package com.xiaoyuan.paperpuppybackendintl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "storage.object-delivery.worker")
public class ObjectDeliveryWorkerProperties {

    /**
     * Disabled by default so existing public R2 URLs keep working during migration.
     */
    private boolean enabled = false;

    private String fileDomain;

    private String imageDomain;

    private String resourceTokenKeyId = "v1";

    private String resourceTokenSecret;

    private long accessTokenDefaultTtlSeconds = 10 * 60;

    private long accessTokenMinTtlSeconds = 60;

    private long accessTokenMaxTtlSeconds = 30 * 60;

    private long defaultFileTtlSeconds = 30 * 60;

    private long defaultImageTtlSeconds = 60 * 60;

}
