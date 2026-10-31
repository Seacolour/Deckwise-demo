package com.xiaoyuan.paperpuppybackendintl.model.enums;

import java.util.Locale;

public enum SessionMessageStatus {
    FINAL,
    STREAMING,
    ABORTED,
    ERROR;

    public String toDatabaseValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SessionMessageStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SessionMessageStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }
}


