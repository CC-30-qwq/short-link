package com.example.shortlink.manager;

import com.example.shortlink.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 缓存管理器（Redis 不可用时自动回退到内存缓存）
 */
@Slf4j
@Component
public class CacheManager {

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    /** 内存缓存（Redis 不可用时的回退方案） */
    private final ConcurrentHashMap<String, String> memoryCache = new ConcurrentHashMap<>();

    private boolean isRedisAvailable() {
        return redisTemplate != null;
    }

    // ========== 短码 -> 原始URL ==========

    public void cacheShortCode(String shortCode, String originalUrl) {
        if (isRedisAvailable()) {
            String key = buildCodeKey(shortCode);
            redisTemplate.opsForValue().set(key, originalUrl, Duration.ofSeconds(Constants.CACHE_TTL_CODE));
        } else {
            memoryCache.put(buildCodeKey(shortCode), originalUrl);
        }
        log.debug("缓存短码映射: {} -> {}", shortCode, originalUrl.substring(0, Math.min(50, originalUrl.length())));
    }

    public String getOriginalUrl(String shortCode) {
        String key = buildCodeKey(shortCode);
        if (isRedisAvailable()) {
            return redisTemplate.opsForValue().get(key);
        }
        return memoryCache.get(key);
    }

    // ========== MD5 -> 短码（防重） ==========

    public void cacheMd5(String md5, String shortCode) {
        if (isRedisAvailable()) {
            String key = buildMd5Key(md5);
            redisTemplate.opsForValue().set(key, shortCode, Duration.ofSeconds(Constants.CACHE_TTL_MD5));
        } else {
            memoryCache.put(buildMd5Key(md5), shortCode);
        }
        log.debug("缓存MD5映射: {} -> {}", md5, shortCode);
    }

    public String getShortCodeByMd5(String md5) {
        String key = buildMd5Key(md5);
        if (isRedisAvailable()) {
            return redisTemplate.opsForValue().get(key);
        }
        return memoryCache.get(key);
    }

    // ========== 缓存失效 ==========

    public void evictShortCode(String shortCode) {
        String key = buildCodeKey(shortCode);
        if (isRedisAvailable()) {
            redisTemplate.delete(key);
        } else {
            memoryCache.remove(key);
        }
    }

    public void evictMd5(String md5) {
        String key = buildMd5Key(md5);
        if (isRedisAvailable()) {
            redisTemplate.delete(key);
        } else {
            memoryCache.remove(key);
        }
    }

    // ========== Key 构建 ==========

    private String buildCodeKey(String shortCode) {
        return String.format(Constants.CACHE_KEY_SHORT_CODE, shortCode);
    }

    private String buildMd5Key(String md5) {
        return String.format(Constants.CACHE_KEY_MD5, md5);
    }
}
