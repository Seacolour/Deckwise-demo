package com.xiaoyuan.paperpuppybackendintl.config;

import com.xiaoyuan.paperpuppybackendintl.annotation.CurrentUserId;
import com.xiaoyuan.paperpuppybackendintl.model.entity.User;
import com.xiaoyuan.paperpuppybackendintl.service.user.AuthService;
import com.xiaoyuan.paperpuppybackendintl.utils.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 解析 @CurrentUserId 参数。
 */
@Component
public class CurrentUserIdResolver implements HandlerMethodArgumentResolver {

    private final AuthService authService;

    public CurrentUserIdResolver(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && (Long.class.equals(parameter.getParameterType()) || long.class.equals(parameter.getParameterType()));
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String token = JwtUtil.getTokenFromRequest(request);
        User user = authService.requireUserFromToken(token);
        return user.getId();
    }
}
