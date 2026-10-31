package com.xiaoyuan.paperpuppybackendintl.aop;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck;
import com.xiaoyuan.paperpuppybackendintl.utils.security.JwtUtil;
import com.xiaoyuan.paperpuppybackendintl.annotation.SkipAuthCheck;
import com.xiaoyuan.paperpuppybackendintl.model.entity.User;
import com.xiaoyuan.paperpuppybackendintl.model.enums.UserRole;
import com.xiaoyuan.paperpuppybackendintl.service.user.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 基于 @AuthCheck 的权限校验切面
 *
 * 目前策略：
 * - 仅拦截显式标注 @AuthCheck 的方法/类；
 * - 支持 @SkipAuthCheck 在方法或类上强制跳过检查；
 * - 默认要求用户已登录；若 mustRole 不为空则额外校验角色。
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthCheckAspect {

    private final AuthService authService;

    @Around("@within(com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck) || " +
            "@annotation(com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck)")
    public Object doAuthCheck(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = method.getDeclaringClass();

        // 若显式标记 SkipAuthCheck，则直接放行
        if (method.isAnnotationPresent(SkipAuthCheck.class) ||
                targetClass.isAnnotationPresent(SkipAuthCheck.class)) {
            return pjp.proceed();
        }

        // 获取 AuthCheck 注解（方法优先，其次类）
        AuthCheck authCheck = method.getAnnotation(AuthCheck.class);
        if (authCheck == null) {
            authCheck = targetClass.getAnnotation(AuthCheck.class);
        }
        if (authCheck == null) {
            // 理论上不会进入这里（因为切点已限定），保护性写法
            return pjp.proceed();
        }

        // 取当前 HTTP 请求
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        if (!(ra instanceof ServletRequestAttributes sra)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }
        HttpServletRequest request = sra.getRequest();

        String token = JwtUtil.getTokenFromRequest(request);
        if (token == null || token.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }

        // 通过应用服务解析并校验当前用户（包含 token 有效性与用户存在性）
        User currentUser = authService.requireUserFromToken(token);

        // 禁用用户禁止访问
        if (UserRole.isBanned(currentUser)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "账号已被封禁，请联系管理员");
        }

        // 角色校验（若配置了 mustRole）
        String mustRole = authCheck.mustRole();
        if (mustRole != null && !mustRole.isEmpty()) {
            if ("admin".equalsIgnoreCase(mustRole) && !UserRole.isAdmin(currentUser)) {
                throw new BusinessException(ErrorCode.NO_ADMIN_ERROR, "无权限，管理员可用");
            }
            // 如需其他角色，可在此扩展判断逻辑
        }

        return pjp.proceed();
    }
}


