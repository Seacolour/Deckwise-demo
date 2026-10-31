package com.xiaoyuan.paperpuppybackendintl.annotation;

import java.lang.annotation.*;

/**
 * @author Seacolour
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedScheduled {

    /**
     * 分布式锁 key，建议格式：lock:模块名:任务名
     */
    String lockKey();

    /**
     * 锁最大持有时间，单位秒（默认 120 秒）
     */
    long leaseTime() default 120;

    /**
     * 获取锁的等待时间，单位秒（默认 5 秒）
     */
    long waitTime() default 5;
}
