package com.xiaoyuan.paperpuppybackendintl.model.entity;

import com.xiaoyuan.paperpuppybackendintl.model.enums.UserRole;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户实体
 */
@Data
public class User {

    private Long id;

    private String email;

    private UserRole role;

    private Boolean emailVerified;

    private String name;

    private String signUpProvider;

    private String preferredLocale;

    private String preferredTimeZone;

    private String lastKnownCountry;

    private Boolean active;

    private Boolean deleted;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime lastLoginAt;
}
