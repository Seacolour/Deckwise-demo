package com.xiaoyuan.paperpuppybackendintl.interceptor;


import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.entity.User;
import com.xiaoyuan.paperpuppybackendintl.service.user.AuthService;
import com.xiaoyuan.paperpuppybackendintl.utils.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    public LoginInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1) 预检请求可直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        final String uri = request.getRequestURI();
        final String httpMethod = request.getMethod();

        // 2) 从请求中更健壮地取 Token
        final String token = JwtUtil.getTokenFromRequest(request);
        if (StringUtils.isBlank(token)) {
            log.warn("【LoginInterceptor】拦截: {} {}, 原因: 未登录/无Bearer", httpMethod, uri);
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录或 token 无效");
        }

        // 3) 复用新项目的鉴权逻辑（JWT + Redis 有效性 + 用户存在性）
        User currentUser = authService.requireUserFromToken(token);
        Long userId = currentUser.getId();

        // 6) 通过
        if (log.isDebugEnabled()) {
            log.debug("【LoginInterceptor】通过: {} {}, userId={}", httpMethod, uri, userId);
        }
        request.setAttribute("loginUserId", userId);
        return true;
    }
}

