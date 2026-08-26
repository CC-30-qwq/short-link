package com.example.shortlink.manager;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.shortlink.common.Constants;
import com.example.shortlink.entity.ShortLink;
import com.example.shortlink.mapper.ShortLinkMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BloomFilterManager 单元测试：布隆过滤器的 Redis 与内存回退两种模式
 */
class BloomFilterManagerTest {

    @Test
    @DisplayName("Redis 不可用时回退内存 Set，add/contains 正常工作")
    void memoryMode_addAndContains() {
        BloomFilterManager m = new BloomFilterManager();
        ReflectionTestUtils.setField(m, "redissonClient", null);
        m.afterPropertiesSet();

        m.add("abc");
        assertThat(m.mightContain("abc")).isTrue();
        assertThat(m.mightContain("xyz")).isFalse();
    }

    @Test
    @DisplayName("Redis 可用时委托给 RBloomFilter")
    void redisMode_delegatesToBloomFilter() {
        RedissonClient client = mock(RedissonClient.class);
        RBloomFilter<String> bloomFilter = mock(RBloomFilter.class);
        when(client.getBloomFilter(anyString())).thenReturn(bloomFilter);
        when(bloomFilter.tryInit(anyLong(), anyDouble())).thenReturn(true);

        BloomFilterManager m = new BloomFilterManager();
        ReflectionTestUtils.setField(m, "redissonClient", client);
        ReflectionTestUtils.setField(m, "expectedInsertions", 1000L);
        ReflectionTestUtils.setField(m, "falseProbability", 0.01);
        m.afterPropertiesSet();

        m.add("abc");
        verify(bloomFilter).add("abc");

        when(bloomFilter.contains("abc")).thenReturn(true);
        assertThat(m.mightContain("abc")).isTrue();
    }

    @Test
    @DisplayName("启动预加载：把库中有效短码回填到过滤器")
    void preload_backfillsValidShortCodes() {
        ShortLinkMapper mapper = mock(ShortLinkMapper.class);
        doAnswer(inv -> {
            Page<ShortLink> page = inv.getArgument(0);
            page.setRecords(Arrays.asList(
                    ShortLink.builder().id(1L).shortCode("abc").status(Constants.STATUS_VALID).build(),
                    ShortLink.builder().id(2L).shortCode("xyz").status(Constants.STATUS_VALID).build()));
            page.setTotal(2);
            return page;
        }).when(mapper).selectPage(any(Page.class), any());

        BloomFilterManager m = new BloomFilterManager();
        ReflectionTestUtils.setField(m, "redissonClient", null);
        ReflectionTestUtils.setField(m, "shortLinkMapper", mapper);
        m.afterPropertiesSet();

        m.preloadExistingShortCodes();

        assertThat(m.mightContain("abc")).isTrue();
        assertThat(m.mightContain("xyz")).isTrue();
        assertThat(m.mightContain("nope")).isFalse();
    }
}
