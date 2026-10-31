package com.xiaoyuan.paperpuppybackendintl.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.common.BaseResponse;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisClient;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisKey;
import com.xiaoyuan.paperpuppybackendintl.utils.NetUtils;
import com.xiaoyuan.paperpuppybackendintl.utils.security.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisClient redisClient;

    /**
     * 粗粒度防护：登录后普通 Web/API 流量不应主要被 IP 桶误伤。
     */
    private static final long DEFAULT_WINDOW_SECONDS = 60;
    private static final long GLOBAL_IP_LIMIT_PER_MINUTE = 600;
    private static final long GLOBAL_USER_LIMIT_PER_MINUTE = 600;
    private static final long PUBLIC_IP_LIMIT_PER_MINUTE = 120;
    private static final long HEAVY_USER_LIMIT_PER_MINUTE = 30;
    private static final long AUTH_IP_WINDOW_SECONDS = 5 * 60;
    private static final long AUTH_IP_LIMIT_PER_WINDOW = 10;
    private static final String HEADER_RETRY_AFTER = "Retry-After";
    private static final String HEADER_RATE_LIMIT_BUCKET = "X-RateLimit-Bucket";
    private static final String HEADER_RATE_LIMIT_LIMIT = "X-RateLimit-Limit";
    private static final String HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    private static final String HEADER_RATE_LIMIT_RESET = "X-RateLimit-Reset";

    private static final Logger accessLogger = LoggerFactory.getLogger("com.xiaoyuan.paperpuppybackendintl.accesslog");

    public RateLimitFilter(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String uri = normalizePath(request);

        // 跳过健康检查和监控端点，避免产生大量无意义的限流记录
        if (shouldSkipRateLimit(request.getMethod(), uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = NetUtils.getIpAddress(request);
        Long userId = resolveUserId(request);
        RateLimitDecision decision = checkRateLimits(resolveBuckets(request.getMethod(), uri, clientIp, userId));
        setRateLimitHeaders(response, decision);
        if (!decision.blocked()) {
            filterChain.doFilter(request, response);
            return;
        }

        log.info("rate-limit blocked: ip={}, userId={}, bucket={}, uri={}",
                clientIp, userId, decision.bucket(), request.getRequestURI());
        // 结构化记录被限流的访问
        accessLogger.info("access_blocked",
                StructuredArguments.keyValue("eventName", "access_blocked"),
                StructuredArguments.keyValue("reason", "rate_limit"),
                StructuredArguments.keyValue("status", HttpStatus.TOO_MANY_REQUESTS.value()),
                StructuredArguments.keyValue("bucket", decision.bucket()),
                StructuredArguments.keyValue("limit", decision.limit()),
                StructuredArguments.keyValue("remaining", decision.remaining()),
                StructuredArguments.keyValue("resetEpochSeconds", decision.resetEpochSeconds()),
                StructuredArguments.keyValue("path", request.getRequestURI()),
                StructuredArguments.keyValue("method", request.getMethod()),
                StructuredArguments.keyValue("ip", clientIp),
                StructuredArguments.keyValue("userId", userId),
                StructuredArguments.keyValue("referer", request.getHeader("Referer")),
                StructuredArguments.keyValue("userAgent", request.getHeader("User-Agent")),
                StructuredArguments.keyValue("timestamp", LocalDateTime.now().toString()),
                StructuredArguments.keyValue("log_type", "access"));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader(HEADER_RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        BaseResponse<Object> resp = new BaseResponse<>(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                null,
                "请求过于频繁，请稍后再试");
        response.getWriter().write(new ObjectMapper().writeValueAsString(resp));
    }

    /**
     * 判断是否为健康检查或监控端点
     */
    private boolean shouldSkipRateLimit(String method, String uri) {
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        return uri.startsWith("/actuator/") ||
                uri.startsWith("/health") ||
                uri.startsWith("/metrics") ||
                uri.startsWith("/_next/static/") ||
                uri.startsWith("/static/") ||
                uri.startsWith("/assets/") ||
                "/favicon.ico".equals(uri);
    }

    private List<RateLimitBucket> resolveBuckets(String method, String uri, String clientIp, Long userId) {
        List<RateLimitBucket> buckets = new ArrayList<>();
        buckets.add(new RateLimitBucket("global.ip", clientIp, GLOBAL_IP_LIMIT_PER_MINUTE, DEFAULT_WINDOW_SECONDS));

        if (isAuthEndpoint(uri)) {
            buckets.add(new RateLimitBucket("auth.ip", clientIp, AUTH_IP_LIMIT_PER_WINDOW, AUTH_IP_WINDOW_SECONDS));
            return buckets;
        }

        if (userId != null) {
            buckets.add(new RateLimitBucket("global.user", String.valueOf(userId),
                    GLOBAL_USER_LIMIT_PER_MINUTE, DEFAULT_WINDOW_SECONDS));
            if (isHeavyEndpoint(method, uri)) {
                buckets.add(new RateLimitBucket("heavy.user", String.valueOf(userId),
                        HEAVY_USER_LIMIT_PER_MINUTE, DEFAULT_WINDOW_SECONDS));
            }
            return buckets;
        }

        buckets.add(new RateLimitBucket("public.ip", clientIp, PUBLIC_IP_LIMIT_PER_MINUTE, DEFAULT_WINDOW_SECONDS));
        return buckets;
    }

    private RateLimitDecision checkRateLimits(List<RateLimitBucket> buckets) {
        List<RateLimitDecision> passed = new ArrayList<>(buckets.size());
        for (RateLimitBucket bucket : buckets) {
            String key = RedisKey.rateLimit(bucket.keySuffix());
            long count = redisClient.incrementWithExpire(key, bucket.windowSeconds(), TimeUnit.SECONDS);
            long retryAfterSeconds = resolveRetryAfterSeconds(key, bucket.windowSeconds());
            RateLimitDecision decision = RateLimitDecision.from(bucket, count, retryAfterSeconds);
            if (decision.blocked()) {
                return decision;
            }
            passed.add(decision);
        }

        return passed.stream()
                .min(Comparator.comparingDouble(RateLimitDecision::remainingRatio))
                .orElseGet(() -> RateLimitDecision.allow("global.ip", GLOBAL_IP_LIMIT_PER_MINUTE,
                        GLOBAL_IP_LIMIT_PER_MINUTE, DEFAULT_WINDOW_SECONDS));
    }

    private long resolveRetryAfterSeconds(String key, long fallbackSeconds) {
        Long ttlSeconds = redisClient.ttl(key, TimeUnit.SECONDS);
        if (ttlSeconds == null || ttlSeconds <= 0) {
            return fallbackSeconds;
        }
        return ttlSeconds;
    }

    private void setRateLimitHeaders(HttpServletResponse response, RateLimitDecision decision) {
        response.setHeader(HEADER_RATE_LIMIT_BUCKET, decision.bucket());
        response.setHeader(HEADER_RATE_LIMIT_LIMIT, String.valueOf(decision.limit()));
        response.setHeader(HEADER_RATE_LIMIT_REMAINING, String.valueOf(decision.remaining()));
        response.setHeader(HEADER_RATE_LIMIT_RESET, String.valueOf(decision.resetEpochSeconds()));
    }

    private Long resolveUserId(HttpServletRequest request) {
        String token = JwtUtil.getTokenFromRequest(request);
        return JwtUtil.getUserId(token);
    }

    private String normalizePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        if (uri.startsWith("/api/")) {
            uri = uri.substring("/api".length());
        }
        return uri.isBlank() ? "/" : uri;
    }

    private boolean isAuthEndpoint(String uri) {
        return uri.equals("/user/login")
                || uri.equals("/user/login/email-code")
                || uri.equals("/user/login/password")
                || uri.equals("/user/password/email-code")
                || uri.equals("/user/password");
    }

    private boolean isHeavyEndpoint(String method, String uri) {
        if ("GET".equalsIgnoreCase(method)) {
            return false;
        }
        String normalized = uri.toLowerCase(Locale.ROOT);
        return normalized.contains("/chat") ||
                normalized.contains("/generate") ||
                normalized.contains("/upload") ||
                normalized.contains("/export") ||
                normalized.contains("/thumbnail") ||
                normalized.contains("/preview-sets") ||
                normalized.contains("/drive/files/import");
    }

    private record RateLimitBucket(String name, String subject, long limit, long windowSeconds) {
        private String keySuffix() {
            return name + ":" + subject;
        }
    }

    private record RateLimitDecision(
            String bucket,
            long limit,
            long remaining,
            long retryAfterSeconds,
            long resetEpochSeconds,
            boolean blocked) {

        private static RateLimitDecision from(RateLimitBucket bucket, long count, long retryAfterSeconds) {
            long remaining = Math.max(0, bucket.limit() - count);
            long resetEpochSeconds = Instant.now().getEpochSecond() + retryAfterSeconds;
            return new RateLimitDecision(
                    bucket.name(),
                    bucket.limit(),
                    remaining,
                    retryAfterSeconds,
                    resetEpochSeconds,
                    count > bucket.limit());
        }

        private static RateLimitDecision allow(String bucket, long limit, long remaining, long retryAfterSeconds) {
            long resetEpochSeconds = Instant.now().getEpochSecond() + retryAfterSeconds;
            return new RateLimitDecision(bucket, limit, remaining, retryAfterSeconds, resetEpochSeconds, false);
        }

        private double remainingRatio() {
            if (limit <= 0) {
                return 0;
            }
            return (double) remaining / (double) limit;
        }
    }
}
