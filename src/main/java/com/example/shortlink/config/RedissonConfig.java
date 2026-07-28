package com.example.shortlink.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端配置（仅在 shortlink.redis.enabled=true 时生效）
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "shortlink.redis.enabled", havingValue = "true")
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    private RedissonClient redissonClient;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + host + ":" + port;
        config.useSingleServer()
                .setAddress(address)
                .setPassword(password.isEmpty() ? null : password)
                .setDatabase(database)
                .setConnectionPoolSize(16)
                .setConnectionMinimumIdleSize(4);

        redissonClient = Redisson.create(config);
        log.info("Redisson 客户端初始化完成: {}", address);
        return redissonClient;
    }

    @PreDestroy
    public void destroy() {
        if (redissonClient != null) {
            redissonClient.shutdown();
            log.info("Redisson 客户端已关闭");
        }
    }
}
