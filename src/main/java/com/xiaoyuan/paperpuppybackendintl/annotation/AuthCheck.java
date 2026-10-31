package com.xiaoyuan.paperpuppybackendintl.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解
 *
 * - 不带参数：仅要求用户已登录
 * - mustRole 指定角色时：要求当前用户具备对应角色（例如 admin）
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 必须具备的角色（如 "admin"），为空则仅要求已登录
     */
    String mustRole() default "";
}


