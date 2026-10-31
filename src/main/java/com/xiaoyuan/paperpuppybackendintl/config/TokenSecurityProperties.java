package com.xiaoyuan.paperpuppybackendintl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Token 安全配置（建议仅通过环境变量注入，不要硬编码/提交到仓库）。
 */
@Data
@ConfigurationProperties(prefix = "app.security")
public class TokenSecurityProperties {

    private Jwt jwt = new Jwt();
    private WebSessionCookie webSessionCookie = new WebSessionCookie();

    @Data
    public static class Jwt {
        /**
         * HMAC 密钥
         */
        private String secret;
        /**
         * 有效期（天）
         */
        private long expirationDays = 30;
        /**
         * 时钟偏差容忍（秒）
         */
        private long leewaySeconds = 60;
    }

    @Data
    public static class WebSessionCookie {
        /**
         * Web 端 httpOnly session cookie 开关。移动端/CLI 仍可继续使用 Bearer token。
         */
        private boolean enabled = true;
        /**
         * Cookie 名称需要和 Next.js proxy 中的服务端判断保持一致。
         */
        private String name = "deckwise_session";
        private String path = "/";
        /**
         * 默认不设置 Domain，让经由 Next.js /api 代理返回的 Set-Cookie 绑定到当前 Web 域名。
         */
        private String domain;
        private boolean httpOnly = true;
        private boolean secure = false;
        private String sameSite = "Lax";
        private long maxAgeDays = 30;
        /**
         * Cookie 会自动随请求发送，因此对 cookie-authenticated 写请求做 Origin/Referer 轻量校验。
         */
        private boolean csrfOriginCheckEnabled = true;
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:3000"));
    }
}


