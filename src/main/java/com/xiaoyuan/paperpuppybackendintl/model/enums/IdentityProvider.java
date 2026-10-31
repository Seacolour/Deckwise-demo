package com.xiaoyuan.paperpuppybackendintl.model.enums;

/**
 * 登录/身份提供方
 */
public enum IdentityProvider {

    GOOGLE,
    APPLE,
    EMAIL,
    GITHUB,
    EMAIL_PASSWORD;

    public static IdentityProvider fromDatabaseValue(String value) {
        if (value == null) {
            return null;
        }
        return IdentityProvider.valueOf(value.toUpperCase());
    }

    public String toDatabaseValue() {
        return name().toLowerCase();
    }
}


