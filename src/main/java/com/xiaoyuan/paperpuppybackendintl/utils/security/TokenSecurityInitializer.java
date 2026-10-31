package com.xiaoyuan.paperpuppybackendintl.utils.security;

import com.xiaoyuan.paperpuppybackendintl.config.TokenSecurityProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 启动时将 application.yml 中的 token 安全配置注入到静态工具类中。
 * <p>
 * 说明：这是“低侵入”方案，避免全量重构成注入式 JwtService。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenSecurityInitializer {

    private final TokenSecurityProperties props;
    private final Environment environment;

    @PostConstruct
    public void init() {
        if (props == null) {
            return;
        }

        var jwt = props.getJwt();
        if (jwt != null) {
            if (StringUtils.hasText(jwt.getSecret())) {
                JwtUtil.configure(jwt.getSecret(), jwt.getExpirationDays(), jwt.getLeewaySeconds());
            } else {
                handleMissingSecret("APP_JWT_SECRET / app.security.jwt.secret", "JWT");
            }
        }

        var webSessionCookie = props.getWebSessionCookie();
        if (webSessionCookie != null) {
            JwtUtil.configureWebSessionCookie(webSessionCookie.isEnabled(), webSessionCookie.getName());
        }
    }

    private void handleMissingSecret(String configKeyHint, String tokenType) {
        // dev/test/local 环境允许使用默认密钥（仅用于本地调试）；prod 环境必须显式配置
        boolean isProdLike = environment != null && environment.acceptsProfiles(
                org.springframework.core.env.Profiles.of("prod", "production")
        );
        if (isProdLike) {
            throw new IllegalStateException(tokenType + " 密钥未配置（" + configKeyHint + "），生产环境禁止使用默认密钥");
        }
        log.warn("{} 密钥未配置（{}），将使用代码中的默认密钥：仅建议本地/测试环境使用！", tokenType, configKeyHint);
    }
}


