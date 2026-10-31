package com.xiaoyuan.paperpuppybackendintl.model.enums;

import java.util.Locale;

public enum SessionMessageRole {
    SYSTEM,
    DEVELOPER,
    USER,
    ASSISTANT,
    TOOL;

    public String toDatabaseValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SessionMessageRole fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SessionMessageRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }
}


