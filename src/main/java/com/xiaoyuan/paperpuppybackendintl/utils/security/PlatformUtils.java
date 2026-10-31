package com.xiaoyuan.paperpuppybackendintl.utils.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 平台工具类
 */
public class PlatformUtils {

    /**
     * 从 X-App-Platform 头中提取渠道：
     * - 如果是 web，就返回 "web"
     * - 如果是 ios-15 或 android-14，则去掉后面的版本号，只保留主渠道标识 "ios"、"android"
     */
    public static String resolvePlatform(HttpServletRequest request) {
        String header = request.getHeader("X-App-Platform");
        if (header == null) {
            return "unknown";
        }
        // 取分隔符 '-' 之前的部分，统一转小写
        String[] parts = header.split("-", 2);
        return parts[0].toLowerCase();
    }
}
