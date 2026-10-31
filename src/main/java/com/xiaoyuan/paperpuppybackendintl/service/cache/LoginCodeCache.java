package com.xiaoyuan.paperpuppybackendintl.service.cache;

import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisClient;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * @author Seacolour
 * @date 2025/12/16 14:13
 * @description:
 */
@Service
@RequiredArgsConstructor
public class LoginCodeCache {

    private static final long EXPIRE_MINUTES = 5;

    private final RedisClient redisClient;

    public void save(String email, String code) {
        redisClient.set(
                RedisKey.loginCode(email),
                code,
                EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );
    }

    public String get(String email) {
        return redisClient.get(RedisKey.loginCode(email));
    }

    public void remove(String email) {
        redisClient.delete(RedisKey.loginCode(email));
    }
}

