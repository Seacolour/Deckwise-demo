package com.xiaoyuan.paperpuppybackendintl.service.monitoring;

/**
 * Redis Key 过期事件（由 RedisKeyEventPublisher 发布）
 */
public record RedisKeyExpiredEvent(String key) {
}
