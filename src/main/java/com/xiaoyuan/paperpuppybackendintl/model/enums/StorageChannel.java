package com.xiaoyuan.paperpuppybackendintl.model.enums;

import java.util.Locale;

public enum StorageChannel {
    PUBLIC("public"),
    PRIVATE("private");

    private final String value;

    StorageChannel(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    public static boolean isPrivate(String value) {
        return PRIVATE.value.equals(normalize(value));
    }

    public static boolean isKnown(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return false;
        }
        for (StorageChannel channel : values()) {
            if (channel.value.equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
