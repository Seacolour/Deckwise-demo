package com.xiaoyuan.paperpuppybackendintl.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisClient {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 安全释放分布式锁（Lua 原子 compare-and-delete）：
     * 仅当 key 的值等于 expectedValue 时才删除，避免误删他人的锁。
     */
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            else
              return 0
            end
            """,
            Long.class
    );

    /**
     * 安全续期分布式锁（Lua 原子 compare-and-pexpire）：
     * 仅当 key 的值等于 expectedValue 时才续期，避免给他人的锁“续命”。
     */
    private static final DefaultRedisScript<Long> COMPARE_AND_PEXPIRE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('pexpire', KEYS[1], ARGV[2])
            else
              return 0
            end
            """,
            Long.class
    );

    /* ---------------- Value ---------------- */

    public void set(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, value, timeout, unit);
        return Boolean.TRUE.equals(ok);
    }

    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    public boolean deleteKey(String key) {
        Boolean ok = stringRedisTemplate.delete(key);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 仅当 key 当前值等于 expectedValue 时删除。
     * 用于释放“SETNX 锁”，支持跨线程释放。
     */
    public boolean deleteIfValue(String key, String expectedValue) {
        if (key == null || key.isBlank() || expectedValue == null || expectedValue.isBlank()) {
            return false;
        }
        Long r = stringRedisTemplate.execute(COMPARE_AND_DELETE_SCRIPT, List.of(key), expectedValue);
        return r != null && r > 0;
    }

    /**
     * 仅当 key 当前值等于 expectedValue 时续期（毫秒级）。
     */
    public boolean pExpireIfValue(String key, String expectedValue, long timeout, TimeUnit unit) {
        if (key == null || key.isBlank() || expectedValue == null || expectedValue.isBlank()) {
            return false;
        }
        if (timeout <= 0) {
            return false;
        }
        long ttlMs = unit.toMillis(timeout);
        Long r = stringRedisTemplate.execute(COMPARE_AND_PEXPIRE_SCRIPT, List.of(key), expectedValue, String.valueOf(ttlMs));
        return r != null && r > 0;
    }

    public boolean exists(String key) {
        Boolean exists = stringRedisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    public Long ttl(String key, TimeUnit unit) {
        return stringRedisTemplate.getExpire(key, unit);
    }

    /* ---------------- Counter ---------------- */

    public Long increment(String key) {
        return stringRedisTemplate.opsForValue().increment(key);
    }

    /**
     * 自增计数，并在首次创建时设置过期时间（用于限流等场景）
     */
    public long incrementWithExpire(String key, long timeout, TimeUnit unit) {
        Long v = stringRedisTemplate.opsForValue().increment(key);
        if (v != null && v == 1L) {
            stringRedisTemplate.expire(key, timeout, unit);
        }
        return v == null ? 0L : v;
    }

    /* ---------------- Set ---------------- */

    public void addToSet(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForSet().add(key, value);
        stringRedisTemplate.expire(key, timeout, unit);
    }

    public Long getSetSize(String key) {
        return stringRedisTemplate.opsForSet().size(key);
    }

    /* ---------------- ZSet ---------------- */

    public void addToZSet(String key, String value, double score, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForZSet().add(key, value, score);
        stringRedisTemplate.expire(key, timeout, unit);
    }

    public Double getZSetScore(String key, String value) {
        return stringRedisTemplate.opsForZSet().score(key, value);
    }

    public Long getZSetSize(String key) {
        return stringRedisTemplate.opsForZSet().size(key);
    }

    public void removeFromZSet(String key, String value) {
        stringRedisTemplate.opsForZSet().remove(key, value);
    }
}
