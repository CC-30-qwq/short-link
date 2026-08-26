package com.example.shortlink.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SnowflakeIdGenerator 单元测试
 * <p>
 * workerId / datacenterId 均为 5 位，取值范围 [0, 31]
 */
class SnowflakeIdGeneratorTest {

    private final SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L, 1L);

    @Test
    @DisplayName("生成的 ID 恒为正数且单调递增")
    void nextIdIsPositiveAndIncreasing() {
        long prev = generator.nextId();
        assertThat(prev).isPositive();
        for (int i = 0; i < 1000; i++) {
            long next = generator.nextId();
            assertThat(next).isPositive();
            assertThat(next).isGreaterThan(prev);
            prev = next;
        }
    }

    @Test
    @DisplayName("连续生成 10 万个 ID 不重复")
    void nextIdIsUnique() {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            ids.add(generator.nextId());
        }
        assertThat(ids).hasSize(100_000);
    }

    @Test
    @DisplayName("workerId 超出范围抛异常")
    void invalidWorkerIdShouldThrow() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(32L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workerId");
    }

    @Test
    @DisplayName("datacenterId 超出范围抛异常")
    void invalidDatacenterIdShouldThrow() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(0L, 32L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("datacenterId");
    }
}
