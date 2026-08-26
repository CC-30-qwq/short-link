package com.example.shortlink.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.shortlink.common.Constants;
import com.example.shortlink.entity.ShortLink;
import com.example.shortlink.mapper.ShortLinkMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

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

    @Autowired(required = false)
    private ShortLinkMapper shortLinkMapper;

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
     * 应用启动完成后，把数据库中已有的有效短码回填到布隆过滤器。
     * <p>
     * 目的：布隆过滤器是内存态、不持久化，应用重启后为空，会导致历史短链首次跳转被误判为不存在。
     * 这里在容器就绪后分页扫描有效短码并批量加入，避免一次性加载全表占用内存。
     * <p>
     * 放在 ApplicationReadyEvent 而非 afterPropertiesSet：确保 DataSource/MyBatis 已完全就绪；
     * 数据库不可用时仅记录告警，不阻塞应用启动。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void preloadExistingShortCodes() {
        if (shortLinkMapper == null) {
            log.info("ShortLinkMapper 不可用，跳过布隆过滤器预加载");
            return;
        }
        try {
            long pageSize = 1000;
            long current = 1;
            long total = 0;
            while (true) {
                Page<ShortLink> page = new Page<>(current, pageSize);
                LambdaQueryWrapper<ShortLink> wrapper = new LambdaQueryWrapper<>();
                wrapper.select(ShortLink::getId, ShortLink::getShortCode)
                        .eq(ShortLink::getStatus, Constants.STATUS_VALID)
                        .orderByAsc(ShortLink::getId);
                shortLinkMapper.selectPage(page, wrapper);
                if (page.getRecords() == null || page.getRecords().isEmpty()) {
                    break;
                }
                for (ShortLink link : page.getRecords()) {
                    add(link.getShortCode());
                }
                total += page.getRecords().size();
                if (!page.hasNext()) {
                    break;
                }
                current++;
            }
            log.info("布隆过滤器预加载完成，回填有效短码 {} 条", total);
        } catch (Exception e) {
            log.warn("布隆过滤器预加载失败（不影响应用启动）: {}", e.getMessage());
        }
    }

}
