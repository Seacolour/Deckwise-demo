package com.xiaoyuan.paperpuppybackendintl.model.enums;

import java.util.Locale;

/**
 * 协作文档类型：
 * - MARKDOWN：文本/Markdown 协作文档
 * 当前仅保留 Markdown 协作文档；其他文件格式作为普通项目文件上传。
 */
public enum DocumentType {
    MARKDOWN;

    public String toDatabaseValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DocumentType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DocumentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }
}
