package com.xiaoyuan.paperpuppybackendintl.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * @author Seacolour
 * @date 2026/1/14 18:25
 * @description:
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    static final String[] CORS_ALLOWED_METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"};

    private final CurrentUserIdResolver currentUserIdResolver;
    private final TokenSecurityProperties tokenSecurityProperties;

    public WebConfig(CurrentUserIdResolver currentUserIdResolver,
                     TokenSecurityProperties tokenSecurityProperties) {
        this.currentUserIdResolver = currentUserIdResolver;
        this.tokenSecurityProperties = tokenSecurityProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // server.servlet.context-path 已经是 /api，MVC 匹配路径不再包含 context path。
        registry.addMapping("/**")
                .allowedOrigins(tokenSecurityProperties.getWebSessionCookie()
                        .getAllowedOrigins().toArray(String[]::new))
                .allowedMethods(CORS_ALLOWED_METHODS)
                .allowedHeaders("*") // 允许所有请求头
                .exposedHeaders("*") // 暴露所有响应头
                .allowCredentials(true) // 关键！允许发送Cookie
                .maxAge(3600); // 预检请求缓存1小时
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserIdResolver);
    }

}
