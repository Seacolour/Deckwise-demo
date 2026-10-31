package com.xiaoyuan.paperpuppybackendintl.model.enums.version;

/**
 * 版本门禁决策类型
 */
public enum VersionDecisionType {
    ALLOW,
    WARN,
    FORCE_UPDATE,
    BLOCK

    ;

    public static VersionDecisionType fromDb(String s) {
        if (s == null) {
            return ALLOW;
        }
        return switch (s.trim().toLowerCase()) {
            case "warn" -> WARN;
            case "force_update" -> FORCE_UPDATE;
            case "block" -> BLOCK;
            default -> ALLOW;
        };
    }

    public static boolean isValidDbValue(String s) {
        if (s == null) {
            return false;
        }
        String v = s.trim().toLowerCase();
        return "allow".equals(v) || "warn".equals(v) || "force_update".equals(v) || "block".equals(v);
    }

    public String toDb() {
        return switch (this) {
            case ALLOW -> "allow";
            case WARN -> "warn";
            case FORCE_UPDATE -> "force_update";
            case BLOCK -> "block";
        };
    }
}


