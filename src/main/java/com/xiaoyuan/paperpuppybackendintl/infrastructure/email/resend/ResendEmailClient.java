package com.xiaoyuan.paperpuppybackendintl.infrastructure.email.resend;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.core.net.RequestOptions;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.xiaoyuan.paperpuppybackendintl.config.ResendProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Resend 客户端（当前仅封装 Send Email）
 *
 * <p>Resend API: POST /emails</p>
 * <p>Doc: https://resend.com/docs/api-reference/emails/send-email</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResendEmailClient {

    private final ResendProperties props;

    /**
     * 官方 SDK 客户端（懒初始化，避免启动时因缺少 API Key 直接失败）
     */
    private volatile Resend resendSdk;

    /**
     * 发送邮件（text/html 二选一或都传；to 最多 50）。
     *
     * @return Resend 返回的 messageId
     */
    public String sendEmail(String to, String subject, String text, String html) {
        return sendEmail(List.of(to), subject, text, html, null, null);
    }

    public String sendEmail(List<String> to, String subject, String text, String html, String replyTo, String idempotencyKey) {
        if (!StringUtils.hasText(props.getApiKey())) {
            throw new IllegalStateException("Resend API Key 未配置（app.resend.api-key / APP_RESEND_API_KEY）");
        }
        if (!StringUtils.hasText(props.getFrom())) {
            throw new IllegalStateException("Resend 发件人未配置（app.resend.from / APP_RESEND_FROM）");
        }
        if (to == null || to.isEmpty()) {
            throw new IllegalArgumentException("收件人不能为空");
        }
        if (!StringUtils.hasText(subject)) {
            throw new IllegalArgumentException("邮件主题不能为空");
        }
        if (!StringUtils.hasText(text) && !StringUtils.hasText(html)) {
            throw new IllegalArgumentException("邮件内容不能为空（text/html 至少提供一个）");
        }

        CreateEmailOptions.Builder builder = CreateEmailOptions.builder()
                .from(props.getFrom())
                .to(to)
                .subject(subject);
        if (StringUtils.hasText(text)) {
            builder.text(text);
        }
        if (StringUtils.hasText(html)) {
            builder.html(html);
        }
        if (StringUtils.hasText(replyTo)) {
            // SDK 支持多个 replyTo；这里按单个使用
            builder.replyTo(replyTo);
        }

        CreateEmailOptions params = builder.build();

        try {
            CreateEmailResponse data;
            if (StringUtils.hasText(idempotencyKey)) {
                RequestOptions options = RequestOptions.builder()
                        .setIdempotencyKey(idempotencyKey)
                        .build();
                data = getResend().emails().send(params, options);
            } else {
                data = getResend().emails().send(params);
            }
            String id = data == null ? null : data.getId();
            if (!StringUtils.hasText(id)) {
                log.warn("[Resend] SendEmail ok but missing id");
            }
            return id;
        } catch (ResendException e) {
            // SDK 已携带 statusCode / responseBody
            log.warn("[Resend] SendEmail failed: status={}, errorName={}, body={}",
                    e.getStatusCode(), e.getErrorName(), safeBody(e.getResponseBody()));
            throw new RuntimeException("Resend 调用失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Resend 调用失败: " + e.getMessage(), e);
        }
    }

    private Resend getResend() {
        Resend c = resendSdk;
        if (c != null) {
            return c;
        }
        synchronized (this) {
            if (resendSdk == null) {
                // 当前 SDK 构造器仅支持 apiKey；baseUrl/timeout 暂无法通过 SDK 注入
                resendSdk = new Resend(props.getApiKey());
            }
            return resendSdk;
        }
    }

    private static String safeBody(String body) {
        if (body == null) {
            return "";
        }
        if (body.length() <= 2000) {
            return body;
        }
        return body.substring(0, 2000) + "...(truncated)";
    }
}

