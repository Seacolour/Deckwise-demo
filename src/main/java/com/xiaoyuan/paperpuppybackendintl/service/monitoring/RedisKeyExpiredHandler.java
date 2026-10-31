package com.xiaoyuan.paperpuppybackendintl.service.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Redis Key 过期事件处理器
 *
 * <p>
 * 当前先做日志与统计入口；后续可在此按 key 前缀路由到具体“数据抢救/告警/回收”逻辑。
 * </p>
 */
@Component
@Slf4j
public class RedisKeyExpiredHandler {

    @EventListener
    public void onExpired(RedisKeyExpiredEvent event) {
        if (event == null || event.key() == null || event.key().isBlank()) {
            return;
        }

        String key = event.key();

        // 过滤掉 localhost 的限流 key（健康检查产生），避免日志噪音
        if (key.contains("rate-limit:localhost")) {
            return;
        }

        // 对于其他限流 key，使用 DEBUG 级别
        if (key.contains("rate-limit:")) {
            log.debug("Redis key expired: {}", key);
            return;
        }

        // 其他重要 key（如锁、session）使用 INFO 级别
        // 注意：过期时 key 对应 value 已被删除，这里只能做告警/统计/补偿触发
        log.info("Redis key expired: {}", key);
    }
}
