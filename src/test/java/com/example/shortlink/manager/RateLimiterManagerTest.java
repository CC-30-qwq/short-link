package com.example.shortlink.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RRateLimiter;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RateLimiterManager 单元测试：限流开关与 Redis 降级放行
 */
class RateLimiterManagerTest {

    @Test
    @DisplayName("限流关闭时直接放行")
    void tryAcquire_whenDisabled() {
        RateLimiterManager m = new RateLimiterManager();
        ReflectionTestUtils.setField(m, "enabled", false);

        assertThat(m.tryAcquire()).isTrue();
    }

    @Test
    @DisplayName("Redis 不可用时降级放行")
    void tryAcquire_whenRedisUnavailable() {
        RateLimiterManager m = new RateLimiterManager();
        ReflectionTestUtils.setField(m, "enabled", true);
        ReflectionTestUtils.setField(m, "redisAvailable", false);

        assertThat(m.tryAcquire()).isTrue();
    }

    @Test
    @DisplayName("Redis 可用且令牌耗尽时返回 false（触发限流）")
    void tryAcquire_whenLimited() {
        RateLimiterManager m = new RateLimiterManager();
        ReflectionTestUtils.setField(m, "enabled", true);
        ReflectionTestUtils.setField(m, "redisAvailable", true);

        RRateLimiter rateLimiter = mock(RRateLimiter.class);
        when(rateLimiter.tryAcquire(anyLong(), any(TimeUnit.class))).thenReturn(false);
        ReflectionTestUtils.setField(m, "rateLimiter", rateLimiter);

        assertThat(m.tryAcquire()).isFalse();
    }
}
