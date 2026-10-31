package com.xiaoyuan.paperpuppybackendintl.model.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 用户签到日志（每日一条）
 */
@Data
public class UserCheckinLog {

    private Long id;

    private Long userId;

    private LocalDate checkinDate;

    private String platform;

    private String appVersion;

    private String deviceType;

    private String deviceToken;

    /**
     * 预留：签到奖励（后续对接积分/额度体系）
     */
    private Integer rewardPoints;

    private Integer streakAfter;

    private String ipAddress;

    private String userAgent;

    private OffsetDateTime createdAt;

    private Boolean deleted;
}


