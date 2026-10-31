package com.xiaoyuan.paperpuppybackendintl.utils.security;

import com.xiaoyuan.paperpuppybackendintl.config.TokenSecurityProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Web 端 session cookie 写入点。Cookie 当前承载现有 JWT，并继续由 Redis token key 兜底失效。
 */
@Component
@RequiredArgsConstructor
public class WebSessionCookieService {

    private final TokenSecurityProperties tokenSecurityProperties;

    public void setSessionCookie(HttpServletResponse response, String token) {
        var props = getProps();
        if (response == null || props == null || !props.isEnabled() || !StringUtils.hasText(token)) {
            return;
        }

        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token, resolveMaxAge(props)).toString());
    }

    public void clearSessionCookie(HttpServletResponse response) {
        var props = getProps();
        if (response == null || props == null || !props.isEnabled()) {
            return;
        }

        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        var props = getProps();
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(props.getName(), value)
                .httpOnly(props.isHttpOnly())
                .secure(props.isSecure())
                .sameSite(resolveSameSite(props.getSameSite()))
                .path(StringUtils.hasText(props.getPath()) ? props.getPath() : "/")
                .maxAge(maxAge);

        if (StringUtils.hasText(props.getDomain())) {
            builder.domain(props.getDomain().trim());
        }

        return builder.build();
    }

    private Duration resolveMaxAge(TokenSecurityProperties.WebSessionCookie props) {
        long days = props.getMaxAgeDays() > 0 ? props.getMaxAgeDays() : 30;
        return Duration.ofDays(days);
    }

    private String resolveSameSite(String value) {
        return StringUtils.hasText(value) ? value.trim() : "Lax";
    }

    private TokenSecurityProperties.WebSessionCookie getProps() {
        return tokenSecurityProperties == null ? null : tokenSecurityProperties.getWebSessionCookie();
    }
}
