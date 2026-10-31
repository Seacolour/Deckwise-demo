package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * User 对外展示 DTO
 */
@Data
public class UserDTO {

    private Long id;

    private String email;

    /**
     * 用户角色（USER / ADMIN ...）
     */
    private String role;

    private Boolean emailVerified;

    private String name;

    private String signUpProvider;

    private OffsetDateTime lastLoginAt;
}


