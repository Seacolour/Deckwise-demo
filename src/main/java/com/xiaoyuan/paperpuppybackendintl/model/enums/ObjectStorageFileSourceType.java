package com.xiaoyuan.paperpuppybackendintl.model.enums;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * object_storage_files.source_type 的应用层来源类型。
 *
 * <p>DB 层只保留 TEXT，避免每次新增文件来源都变成 schema migration。</p>
 */
public enum ObjectStorageFileSourceType {
    BYTES("bytes"),
    PUBLISH("publish"),
    COPY("copy"),
    DERIVED("derived"),
    GENERATED("generated");

    private static final List<String> CACHE_KEY_LOOKUP_VALUES = List.of(
            BYTES.value,
            PUBLISH.value,
            COPY.value,
            DERIVED.value,
            GENERATED.value
    );

    private static final Set<String> PROJECT_DELETE_AUTO_PURGE_VALUES = Set.of(
            PUBLISH.value,
            COPY.value,
            DERIVED.value,
            GENERATED.value
    );

    private final String value;

    ObjectStorageFileSourceType(String value) {
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

    public static boolean isKnown(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return false;
        }
        for (ObjectStorageFileSourceType sourceType : values()) {
            if (sourceType.value.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static String resolvePublicUploadSourceType(String uploadSource) {
        String normalized = normalize(uploadSource);
        if (normalized == null) {
            return GENERATED.value;
        }
        if ("user_upload".equals(normalized)
                || "user-upload".equals(normalized)
                || "user".equals(normalized)
                || "upload".equals(normalized)
                || "reusable".equals(normalized)
                || BYTES.value.equals(normalized)) {
            return BYTES.value;
        }
        if ("ai_generated".equals(normalized)
                || "ai-generated".equals(normalized)
                || "system_generated".equals(normalized)
                || "system-generated".equals(normalized)
                || GENERATED.value.equals(normalized)) {
            return GENERATED.value;
        }
        return null;
    }

    public static List<String> cacheKeyLookupValues() {
        return CACHE_KEY_LOOKUP_VALUES;
    }

    public static Set<String> projectDeleteAutoPurgeValues() {
        return PROJECT_DELETE_AUTO_PURGE_VALUES;
    }
}
