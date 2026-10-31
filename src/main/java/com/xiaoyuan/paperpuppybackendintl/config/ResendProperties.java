package com.xiaoyuan.paperpuppybackendintl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Resend 邮件服务配置
 *
 * <p>建议在生产环境通过环境变量注入：</p>
 * <ul>
 *   <li>APP_RESEND_API_KEY</li>
 *   <li>APP_RESEND_FROM（例如：PaperPuppy <no-reply@your-domain.com>）</li>
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "app.resend")
@Data
public class ResendProperties {

    /**
     * Resend API Key（形如 re_xxx）
     */
    private String apiKey;

    /**
     * 默认发件人（Resend 要求 from 必填）
     * 格式："Your Name <sender@domain.com>"
     */
    private String from;
}

