package com.xiaoyuan.paperpuppybackendintl.model.entity;

import com.xiaoyuan.paperpuppybackendintl.model.enums.IdentityProvider;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户登录记录
 */
@Data
public class UserLoginRecord {

    private Long id;

    private Long userId;

    private OffsetDateTime loginTime;

    private String ipAddress;

    private String userAgent;

    private IdentityProvider provider;

    private Boolean success;
}


