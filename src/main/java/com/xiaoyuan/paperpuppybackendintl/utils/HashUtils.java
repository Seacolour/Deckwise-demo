package com.xiaoyuan.paperpuppybackendintl.utils;

import java.security.MessageDigest;

/**
 * Hash 工具（用于文件去重等）
 */
public final class HashUtils {

    private HashUtils() {
    }

    public static String sha256Hex(String text) {
        if (text == null) {
            return null;
        }
        return sha256Hex(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}


