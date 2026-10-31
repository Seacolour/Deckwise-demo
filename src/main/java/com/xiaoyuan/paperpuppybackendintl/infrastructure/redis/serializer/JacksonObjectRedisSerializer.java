package com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * 自定义 Jackson RedisSerializer（避免 Spring Data Redis 4.x 中 Jackson2/GenericJackson2 序列化器的弃用问题）
 *
 * <p>依赖 ObjectMapper（可配置默认类型信息、JavaTimeModule 等）。</p>
 */
public class JacksonObjectRedisSerializer implements RedisSerializer<Object> {

    private final ObjectMapper objectMapper;

    public JacksonObjectRedisSerializer(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(Object value) throws SerializationException {
        if (value == null) {
            return new byte[0];
        }
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new SerializationException("Could not serialize value", e);
        }
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(bytes, Object.class);
        } catch (Exception e) {
            throw new SerializationException("Could not deserialize value", e);
        }
    }
}


