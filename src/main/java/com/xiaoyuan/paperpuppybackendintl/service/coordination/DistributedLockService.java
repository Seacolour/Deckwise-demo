package com.xiaoyuan.paperpuppybackendintl.service.coordination;

import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisLockClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final RedisLockClient lockClient;

    public boolean tryLock(String key, long waitMs, long leaseMs) {
        return lockClient.tryLock(key, waitMs, leaseMs, TimeUnit.MILLISECONDS);
    }

    public void unlock(String key) {
        lockClient.unlock(key);
    }

    /**
     * 执行一次性任务（拿不到锁直接跳过）
     */
    public void runIfLockAcquired(
            String key,
            long waitMs,
            long leaseMs,
            Runnable task
    ) {
        boolean locked = tryLock(key, waitMs, leaseMs);
        if (!locked) {
            return;
        }
        try {
            task.run();
        } finally {
            unlock(key);
        }
    }
}
