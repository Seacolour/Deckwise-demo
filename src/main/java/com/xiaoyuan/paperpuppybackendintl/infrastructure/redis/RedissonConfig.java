package com.xiaoyuan.paperpuppybackendintl.infrastructure.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.util.StringUtils;

@Configuration
@Profile("prod")
public class RedissonConfig {

    /**
     * prod 默认支持 Sentinel；但在“先快速上线的单机 Redis 环境”下允许缺省并自动降级到单机模式，
     * 避免因未配置 spring.data.redis.sentinel.* 导致启动失败。
     */
    @Value("${spring.data.redis.sentinel.master:}")
    private String master;

    @Value("${spring.data.redis.sentinel.nodes:}")
    private String nodes;

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Bean(destroyMethod = "shutdown")
    @SuppressWarnings("deprecation")
    public RedissonClient redissonClient() {
        Config config = new Config();
        boolean enableSentinel = StringUtils.hasText(master) && StringUtils.hasText(nodes);
        if (enableSentinel) {
            var sentinel = config.useSentinelServers()
                    .setMasterName(master)
                    .setDatabase(database);

            for (String node : nodes.split(",")) {
                if (StringUtils.hasText(node)) {
                    sentinel.addSentinelAddress("redis://" + node.trim());
                }
            }

            if (StringUtils.hasText(password)) {
                sentinel.setPassword(password);
            }
        } else {
            var single = config.useSingleServer()
                    .setAddress("redis://" + host + ":" + port)
                    .setDatabase(database);
            if (StringUtils.hasText(password)) {
                single.setPassword(password);
            }
        }

        return Redisson.create(config);
    }
}
