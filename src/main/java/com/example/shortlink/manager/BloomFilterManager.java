package com.example.shortlink.manager;

import com.example.shortlink.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 布隆过滤器管理器（Redis 不可用时自动回退到内存 Set）
 */
@Slf4j
@Component
public class BloomFilterManager implements InitializingBean {

    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Value("${shortlink.bloom-filter.expected-insertions:1000000}")
    private long expectedInsertions;

    @Value("${shortlink.bloom-filter.false-probability:0.01}")
    private double falseProbability;

    private RBloomFilter<String> bloomFilter;

    /** 内存 Set（Redis 不可用时的回退方案） */
    private final Set<String> memorySet = ConcurrentHashMap.newKeySet();

    private boolean redisAvailable;

    @Override
    public void afterPropertiesSet() {
        redisAvailable = (redissonClient != null);
        if (redisAvailable) {
            bloomFilter = redissonClient.getBloomFilter(Constants.BLOOM_FILTER_NAME);
            boolean initialized = bloomFilter.tryInit(expectedInsertions, falseProbability);
            if (initialized) {
                log.info("布隆过滤器初始化完成: expectedInsertions={}, falseProbability={}", expectedInsertions, falseProbability);
            } else {
                log.info("布隆过滤器已存在，当前元素数: {}", bloomFilter.count());
            }
        } else {
            log.info("Redis 不可用，使用内存 Set 替代布隆过滤器（当前元素数: {}）", memorySet.size());
        }
    }

    /**
     * 判断短码可能存在
     */
    public boolean mightContain(String shortCode) {
        if (redisAvailable) {
            return bloomFilter.contains(shortCode);
        }
        // 内存模式：直接返回 false，让请求走缓存/数据库查询
        return memorySet.contains(shortCode);
    }

    /**
     * 将短码添加到过滤器
     */
    public void add(String shortCode) {
        if (redisAvailable) {
            bloomFilter.add(shortCode);
        } else {
            memorySet.add(shortCode);
        }
        log.debug("短码已加入布隆过滤器: {}", shortCode);
    }

    /**
     * 获取当前过滤器中的元素数量
     */
    public long count() {
        if (redisAvailable) {
            return bloomFilter.count();
        }
        return memorySet.size();
    }
}
