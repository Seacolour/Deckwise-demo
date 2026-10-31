package com.xiaoyuan.paperpuppybackendintl.model.entity;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户设备
 */
@Data
public class UserDevice {

    private Long id;

    private Long userId;

    private String deviceToken;

    private String deviceType;

    private String environment;

    private OffsetDateTime lastActiveAt;

    private OffsetDateTime createdAt;
}


