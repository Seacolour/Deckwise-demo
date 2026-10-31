package com.xiaoyuan.paperpuppybackendintl.model.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 用户签到状态（连续天数）
 */
@Data
public class UserCheckinStatus {

    private Long userId;

    private Integer streakCount;

    private Integer longestStreak;

    private LocalDate lastCheckinDate;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private Boolean deleted;
}


