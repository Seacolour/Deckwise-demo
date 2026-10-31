package com.xiaoyuan.paperpuppybackendintl.aop;

import com.xiaoyuan.paperpuppybackendintl.utils.NetUtils;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Aspect
@Component
@Slf4j
public class LogInterceptor {

    private static final int MAX_ARG_LENGTH = 200;
    private static final Pattern SENSITIVE_VALUE_PATTERN = Pattern.compile(
            "(?i)((?<![A-Za-z0-9_])[\\\"']?(?:idToken|accessToken|refreshToken|userToken|token|password|"
                    + "passcode|code|verificationCode|emailCode|authorizationCode|authorization|clientSecret|"
                    + "pollSecret|secret|apiKey|privateKey|credential|nonce)[\\\"']?\\s*[=:]\\s*[\\\"']?)"
                    + "([^,;&\\]\\}\\)\\\"']+)"
    );

    @Around("@annotation(org.springframework.web.bind.annotation.RequestMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.GetMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PatchMapping)")
    public Object doInterceptor(ProceedingJoinPoint point) throws Throwable {
        // 计时
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // 拿到当前线程的 RequestAttributes
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = null;
        String requestId = UUID.randomUUID().toString();
        String url;
        String params;
        String method = null;
        String ip = null;

        // 只有在 HTTP 请求线程中，ra 才是 ServletRequestAttributes
        if (ra instanceof ServletRequestAttributes sra) {
            request = sra.getRequest();
            method = request.getMethod();
            String originalUri = (String) request.getAttribute(
                    RequestDispatcher.ERROR_REQUEST_URI);
            url = originalUri != null
                    ? originalUri
                    : request.getRequestURI();

            String qs = request.getQueryString();
            if (qs != null && !qs.isBlank()) {
                url = url + "?" + qs;
            }
            url = redactSensitiveValues(url);
            // 使用 NetUtils 获取真实客户端 IP（处理反向代理和 Docker 网络）
            ip = NetUtils.getIpAddress(request);
            params = summarizeArgs(point.getArgs());
            log.info("request start, id: {}, method: {}, path: {}, ip: {}, params: {}",
                    requestId, method, url, ip, params);
        }

        // 执行目标方法
        Throwable failure = null;
        try {
            return point.proceed();
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            // 停表并打印结束日志（只在有 request 时）
            stopWatch.stop();
            if (request != null) {
                long cost = stopWatch.getTotalTimeMillis();
                if (failure == null) {
                    log.info("request end, id: {}, cost: {} ms", requestId, cost);
                }
            }
        }
    }

    private String summarizeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        return "[" + Arrays.stream(args)
                .map(this::summarizeArg)
                .collect(Collectors.joining(", ")) + "]";
    }

    String summarizeArg(Object arg) {
        if (arg == null) {
            return "null";
        }
        String s = redactSensitiveValues(arg.toString());
        if (s.length() > MAX_ARG_LENGTH) {
            return s.substring(0, MAX_ARG_LENGTH) + "...(" + s.length() + " chars)";
        }
        return s;
    }

    static String redactSensitiveValues(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        Matcher matcher = SENSITIVE_VALUE_PATTERN.matcher(value);
        StringBuilder redacted = new StringBuilder(value.length());
        while (matcher.find()) {
            matcher.appendReplacement(
                    redacted,
                    Matcher.quoteReplacement(matcher.group(1) + "<redacted>"));
        }
        matcher.appendTail(redacted);
        return redacted.toString();
    }

}
