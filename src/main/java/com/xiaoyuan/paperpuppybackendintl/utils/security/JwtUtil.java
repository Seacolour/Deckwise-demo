package com.xiaoyuan.paperpuppybackendintl.utils.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类（基于 auth0 java-jwt）
 * 只在 token 中存放最小必要信息：userId + email
 */
public final class JwtUtil {

    /**
     * 对称加密密钥（生产环境请通过 {@link #configure(String, long, long)} 覆盖！）
     */
    private static final String DEFAULT_JWT_SECRET = "wdlrznU0omc2MDwCOn3zAVVEsGEO41UF";

    /**
     * token 有效期（毫秒），这里默认 30 天
     */
    private static final long DEFAULT_JWT_EXPIRATION_MS = 30L * 24 * 60 * 60 * 1000;

    private static volatile long expirationMs = DEFAULT_JWT_EXPIRATION_MS;
    private static volatile long leewaySeconds = 60;
    private static volatile Algorithm algorithm =
            Algorithm.HMAC256(DEFAULT_JWT_SECRET.getBytes(StandardCharsets.UTF_8));
    private static volatile boolean webSessionCookieEnabled = true;
    private static volatile String webSessionCookieName = "deckwise_session";

    private JwtUtil() {
    }

    /**
     * 运行时配置（通常由 Spring 在启动时调用）。
     *
     * @param jwtSecret      HMAC 密钥
     * @param expirationDays token 有效期（天）
     * @param leewaySeconds  允许的时钟偏差（秒）
     */
    public static void configure(String jwtSecret, long expirationDays, long leewaySeconds) {
        if (jwtSecret != null && !jwtSecret.isBlank()) {
            algorithm = Algorithm.HMAC256(jwtSecret.getBytes(StandardCharsets.UTF_8));
        }
        if (expirationDays > 0) {
            expirationMs = expirationDays * 24L * 60 * 60 * 1000;
        }
        if (leewaySeconds >= 0) {
            JwtUtil.leewaySeconds = leewaySeconds;
        }
    }

    public static void configureWebSessionCookie(boolean enabled, String cookieName) {
        webSessionCookieEnabled = enabled;
        if (hasText(cookieName)) {
            webSessionCookieName = cookieName.trim();
        }
    }

    /**
     * 生成 JWT token
     *
     * @param userId 用户 id
     * @param email  用户邮箱（可选，用于调试或前端展示）
     * @return JWT 字符串
     */
    public static String generateToken(Long userId, String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return JWT.create()
                .withJWTId(UUID.randomUUID().toString())
                .withSubject(String.valueOf(userId))
                .withClaim("email", email)
                .withIssuedAt(now)
                .withExpiresAt(expiryDate)
                .sign(algorithm);
    }

    /**
     * 从请求中提取登录 token：移动端/CLI 的 Bearer token 优先，Web 端 httpOnly cookie 兜底。
     */
    public static String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = getBearerTokenFromRequest(request);
        if (hasText(bearerToken)) {
            return bearerToken;
        }
        return getWebSessionCookieToken(request);
    }

    /**
     * 从请求中提取 Authorization: Bearer token。
     */
    public static String getBearerTokenFromRequest(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String header = request.getHeader("Authorization");
        if (header == null) {
            return null;
        }
        if (header.length() >= 7 && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        return null;
    }

    /**
     * 从 Web 端 httpOnly session cookie 中提取 token。
     */
    public static String getWebSessionCookieToken(HttpServletRequest request) {
        if (!webSessionCookieEnabled || request == null) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie == null || !webSessionCookieName.equals(cookie.getName())) {
                continue;
            }
            String value = cookie.getValue();
            return hasText(value) ? value.trim() : null;
        }
        return null;
    }

    /**
     * 校验并解析 token，返回 DecodedJWT；失败则返回 null
     */
    public static DecodedJWT validateToken(String token) {
        try {
            if (token == null || token.isEmpty()) {
                return null;
            }
            return JWT.require(algorithm)
                    .acceptLeeway(leewaySeconds)
                    .build()
                    .verify(token);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 token 中获取用户 id（subject）
     */
    public static Long getUserId(String token) {
        DecodedJWT jwt = validateToken(token);
        if (jwt == null) {
            return null;
        }
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从 token 中获取 email
     */
    public static String getEmail(String token) {
        DecodedJWT jwt = validateToken(token);
        if (jwt == null) {
            return null;
        }
        return jwt.getClaim("email").asString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}


