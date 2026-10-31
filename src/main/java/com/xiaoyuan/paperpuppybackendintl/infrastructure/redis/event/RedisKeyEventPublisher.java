package com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.event;


import com.xiaoyuan.paperpuppybackendintl.service.monitoring.RedisKeyExpiredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Slf4j
public class RedisKeyEventPublisher extends KeyExpirationEventMessageListener {

    private final ApplicationEventPublisher publisher;

    public RedisKeyEventPublisher(
            RedisMessageListenerContainer container,
            ApplicationEventPublisher publisher
    ) {
        super(container);
        this.publisher = publisher;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = message.toString();
        publisher.publishEvent(new RedisKeyExpiredEvent(key));
    }
}
