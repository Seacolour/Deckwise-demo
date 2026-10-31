package com.xiaoyuan.paperpuppybackendintl.model.enums;

import com.xiaoyuan.paperpuppybackendintl.model.entity.User;

/**
 * 用户角色枚举
 *
 * 与数据库 role 字段取值一致：user / admin / ban
 */
public enum UserRole {

    USER,
    ADMIN,
    BAN;

    public static UserRole fromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (UserRole role : values()) {
            if (role.name().equalsIgnoreCase(value)) {
                return role;
            }
        }
        return null;
    }

    public static boolean isAdmin(User user) {
        return user != null && ADMIN.equals(user.getRole());
    }

    public static boolean isBanned(User user) {
        return user != null && BAN.equals(user.getRole());
    }
}


