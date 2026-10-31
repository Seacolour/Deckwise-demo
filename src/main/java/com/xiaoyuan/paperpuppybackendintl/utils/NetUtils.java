package com.xiaoyuan.paperpuppybackendintl.utils;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * 网络工具类
 */
public final class NetUtils {

    private static final String UNKNOWN_IP = "unknown";
    private static final List<String> TRUSTED_PROXY_HEADERS = List.of(
            "X-Forwarded-For",
            "X-Real-IP",
            "CF-Connecting-IP");

    private NetUtils() {
    }

    /**
     * 获取客户端 IP 地址
     * <p>
     * 支持 IPv4 和 IPv6，统一处理 localhost 变体
     * </p>
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址
     */
    public static String getIpAddress(HttpServletRequest request) {
        String remoteAddress = validIpLiteral(request.getRemoteAddr());

        // Proxy headers are meaningful only when the direct servlet peer is a
        // local/private reverse proxy. A publicly connected peer can never
        // choose the address used by logging, rate limiting, or geo lookups.
        if (isTrustedProxy(remoteAddress)) {
            for (String header : TRUSTED_PROXY_HEADERS) {
                String forwardedAddress = validForwardedIp(request.getHeader(header));
                if (forwardedAddress != null) {
                    return normalizeLocalhost(forwardedAddress);
                }
            }
        }

        return remoteAddress == null ? UNKNOWN_IP : normalizeLocalhost(remoteAddress);
    }

    private static String validForwardedIp(String value) {
        if (value == null || value.isBlank() || UNKNOWN_IP.equalsIgnoreCase(value.trim())) {
            return null;
        }
        // Nginx sends one canonical address. Never interpret a client-provided
        // comma-separated proxy chain inside the application.
        if (value.indexOf(',') >= 0) {
            return null;
        }
        return validIpLiteral(value);
    }

    private static String validIpLiteral(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String candidate = value.trim();
        if (isValidIpv4(candidate)) {
            return candidate;
        }

        // A colon plus this character allowlist makes InetAddress parsing
        // strictly IPv6-shaped and prevents hostname/DNS resolution.
        if (candidate.indexOf(':') < 0 || !candidate.matches("[0-9A-Fa-f:.]+")) {
            return null;
        }
        try {
            InetAddress.getByName(candidate);
            return candidate;
        } catch (UnknownHostException ignored) {
            return null;
        }
    }

    private static boolean isValidIpv4(String candidate) {
        String[] parts = candidate.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                return false;
            }
            try {
                if (Integer.parseInt(part) > 255) {
                    return false;
                }
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTrustedProxy(String ip) {
        if (ip == null) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress()) {
                return true;
            }
            byte[] bytes = address.getAddress();
            return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
        } catch (UnknownHostException ignored) {
            return false;
        }
    }

    private static String normalizeLocalhost(String ip) {
        return isLocalhost(ip) ? "localhost" : ip;
    }

    /**
     * 判断是否为 localhost
     * <p>
     * 支持 IPv4 (127.0.0.1) 和 IPv6 (::1, 0:0:0:0:0:0:0:1)
     * </p>
     */
    public static boolean isLocalhost(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        // IPv4 localhost
        if ("127.0.0.1".equals(ip)) {
            return true;
        }
        // IPv6 localhost variants
        return "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
    }

    /**
     * 判断是否为私有 IP 地址
     */
    public static boolean isPrivateIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        if (isLocalhost(ip) || "localhost".equalsIgnoreCase(ip)) {
            return true;
        }

        // IPv4 private ranges
        // 10.0.0.0/8
        if (ip.startsWith("10.")) {
            return true;
        }
        // 192.168.0.0/16
        if (ip.startsWith("192.168.")) {
            return true;
        }
        // 172.16.0.0/12 (172.16.0.0 - 172.31.255.255)
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                try {
                    int secondOctet = Integer.parseInt(parts[1]);
                    if (secondOctet >= 16 && secondOctet <= 31) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }

        return false;
    }
}
