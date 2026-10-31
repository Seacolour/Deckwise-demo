package com.xiaoyuan.paperpuppybackendintl.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记此 Controller 类或方法跳过权限检查
 *
 * 目前预留给后续全局权限拦截使用，当前 AuthCheckAspect 仅在显式使用 @AuthCheck 时生效。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SkipAuthCheck {
}


