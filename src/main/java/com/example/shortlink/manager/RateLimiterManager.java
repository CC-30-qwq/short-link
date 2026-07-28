package com.example.shortlink.manager;

import com.example.shortlink.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * 限流管理器（Redis 不可用时自动放行）
 */
@Slf4j
@Component
public class RateLimiterManager {

    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Value("${shortlink.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${shortlink.rate-limit.permits-per-second:100}")
    private long permitsPerSecond;

    @Value("${shortlink.rate-limit.acquire-timeout-seconds:3}")
    private long acquireTimeoutSeconds;

    private RRateLimiter rateLimiter;
    private boolean redisAvailable;

    @PostConstruct
    public void init() {
        redisAvailable = (redissonClient != null);
        if (redisAvailable) {
            rateLimiter = redissonClient.getRateLimiter(Constants.RATE_LIMITER_KEY);
            boolean set = rateLimiter.trySetRate(RateType.OVERALL, permitsPerSecond, 1, RateIntervalUnit.SECONDS);
            if (set) {
                log.info("限流器初始化完成: {}次/秒", permitsPerSecond);
            }
        } else {
            log.info("Redis 不可用，限流器关闭（所有请求直接放行）");
        }
    }

    /**
     * 尝试获取一个令牌（Redis 不可用时直接放行）
     */
    public boolean tryAcquire() {
        if (!enabled) {
            return true;
        }
        if (!redisAvailable) {
            return true;
        }
        boolean acquired = rateLimiter.tryAcquire(acquireTimeoutSeconds, TimeUnit.SECONDS);
        if (!acquired) {
            log.warn("触发限流: permits/s={}", permitsPerSecond);
        }
        return acquired;
    }
}
