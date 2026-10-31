package com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.event;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@Slf4j
public class RedisEventConfig {

    private final RedisConnectionFactory factory;
    private final ApplicationEventPublisher publisher;

    public RedisEventConfig(RedisConnectionFactory factory,
                            ApplicationEventPublisher publisher) {
        this.factory = factory;
        this.publisher = publisher;
    }

    @PostConstruct
    public void ensureKeyspaceNotifications() {
        try (var conn = factory.getConnection()) {
            var props = conn.serverCommands().getConfig("notify-keyspace-events");
            String flags = (props != null ? props.getProperty("notify-keyspace-events", "") : "").trim();

            boolean hasX = flags.indexOf('x') >= 0; // expired
            boolean hasE = flags.indexOf('E') >= 0; // keyevent
            boolean hasK = flags.indexOf('K') >= 0; // keyspace
            boolean enabled = hasX && (hasE || hasK);

            if (!enabled) {
                // 我们监听 keyevent 的 expired：__keyevent@db__:expired → 需要 Ex
                String target = "Ex";
                conn.serverCommands().setConfig("notify-keyspace-events", target);
                log.info("✅ Redis notify-keyspace-events set to {}", target);
            } else {
                log.info("✅ Redis notify-keyspace-events already enabled: '{}'", flags);
            }
        }
    }

    @Bean
    public RedisMessageListenerContainer listenerContainer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        return container;
    }

    @Bean
    public RedisKeyEventPublisher redisKeyEventPublisher(RedisMessageListenerContainer container) {
        return new RedisKeyEventPublisher(container, publisher);
    }
}
