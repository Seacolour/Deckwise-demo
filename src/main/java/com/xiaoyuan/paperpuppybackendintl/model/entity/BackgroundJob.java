package com.xiaoyuan.paperpuppybackendintl.model.entity;

import lombok.Data;
import org.jooq.JSONB;

import java.time.OffsetDateTime;

@Data
public class BackgroundJob {

    private Long id;

    private String jobType;
    private String dedupKey;
    private String status;
    private Integer priority;

    private JSONB payload;
    private JSONB result;
    private JSONB metadata;
    private String errorMessage;

    private Integer attempts;
    private Integer maxAttempts;
    private OffsetDateTime nextRunAt;

    private String lockedBy;
    private OffsetDateTime lockedAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;

    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
