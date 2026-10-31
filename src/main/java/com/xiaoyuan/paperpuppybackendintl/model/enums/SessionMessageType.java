package com.xiaoyuan.paperpuppybackendintl.model.enums;

import java.util.Locale;

public enum SessionMessageType {
    TEXT,
    TOOL_OUTPUT,
    METADATA,
    IMAGE;

    public String toDatabaseValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SessionMessageType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SessionMessageType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }
}


