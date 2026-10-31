package com.xiaoyuan.paperpuppybackendintl.config;

import com.xiaoyuan.paperpuppybackendintl.utils.security.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cookie 鉴权会自动随请求发送；对使用 Web session cookie 的写请求做轻量 CSRF Origin 校验。
 */
@Component
@RequiredArgsConstructor
public class WebSessionCsrfFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final TokenSecurityProperties tokenSecurityProperties;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!shouldCheck(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isAllowedWebOrigin(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.sendError(HttpServletResponse.SC_FORBIDDEN, "CSRF origin check failed");
    }

    private boolean shouldCheck(HttpServletRequest request) {
        var props = props();
        if (request == null || props == null || !props.isEnabled() || !props.isCsrfOriginCheckEnabled()) {
            return false;
        }

        String method = request.getMethod();
        if (method != null && SAFE_METHODS.contains(method.toUpperCase(Locale.ROOT))) {
            return false;
        }

        if (StringUtils.hasText(JwtUtil.getBearerTokenFromRequest(request))) {
            return false;
        }

        return StringUtils.hasText(JwtUtil.getWebSessionCookieToken(request));
    }

    private boolean isAllowedWebOrigin(HttpServletRequest request) {
        String requestOrigin = resolveRequestOrigin(request);
        if (!StringUtils.hasText(requestOrigin)) {
            return false;
        }

        Set<String> allowedOrigins = props().getAllowedOrigins().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());

        return allowedOrigins.contains(requestOrigin);
    }

    private String resolveRequestOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (StringUtils.hasText(origin)) {
            return normalizeOrigin(origin);
        }

        String referer = request.getHeader("Referer");
        if (!StringUtils.hasText(referer)) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                return null;
            }
            int port = uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + (port >= 0 ? ":" + port : "");
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String normalizeOrigin(String origin) {
        String normalized = origin.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private TokenSecurityProperties.WebSessionCookie props() {
        return tokenSecurityProperties == null ? null : tokenSecurityProperties.getWebSessionCookie();
    }
}
