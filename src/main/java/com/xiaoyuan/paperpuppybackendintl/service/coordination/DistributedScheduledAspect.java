package com.xiaoyuan.paperpuppybackendintl.service.coordination;

import com.xiaoyuan.paperpuppybackendintl.annotation.DistributedScheduled;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class DistributedScheduledAspect {

    private final DistributedLockService lockService;

    @Around("@annotation(distributedScheduled)")
    public Object around(
            ProceedingJoinPoint joinPoint,
            DistributedScheduled distributedScheduled
    ) throws Throwable {

        String rawKey = distributedScheduled.lockKey();
        String lockKey = RedisKey.lock(rawKey);

        long waitTime = distributedScheduled.waitTime();
        long leaseTime = distributedScheduled.leaseTime();

        boolean locked = lockService.tryLock(
                lockKey,
                TimeUnit.SECONDS.toMillis(waitTime),
                TimeUnit.SECONDS.toMillis(leaseTime)
        );

        if (!locked) {
            log.info("⏭️ 分布式锁未获取，跳过任务: {}", lockKey);
            return null;
        }

        try {
            log.info("✅ 获取锁，执行定时任务: {}", lockKey);
            return joinPoint.proceed();
        } finally {
            lockService.unlock(lockKey);
            log.info("🔓 释放锁: {}", lockKey);
        }
    }
}
