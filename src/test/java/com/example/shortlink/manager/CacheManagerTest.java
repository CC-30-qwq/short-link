package com.example.shortlink.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CacheManager 单元测试：Redis 不可用时回退内存缓存的读写
 */
class CacheManagerTest {

    @Test
    @DisplayName("短码缓存：写入后能读到，未写入返回 null")
    void memoryMode_shortCodeCache() {
        CacheManager m = new CacheManager(); // redisTemplate 默认 null，走内存模式

        m.cacheShortCode("abc", "http://example.com");

        assertThat(m.getOriginalUrl("abc")).isEqualTo("http://example.com");
        assertThat(m.getOriginalUrl("nope")).isNull();
    }

    @Test
    @DisplayName("MD5 缓存：写入后能读到")
    void memoryMode_md5Cache() {
        CacheManager m = new CacheManager();

        m.cacheMd5("md5abc", "xyz");

        assertThat(m.getShortCodeByMd5("md5abc")).isEqualTo("xyz");
        assertThat(m.getShortCodeByMd5("other")).isNull();
    }
}
