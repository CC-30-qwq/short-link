package com.example.shortlink.common;

import java.time.Duration;

/**
 * 系统常量定义
 */
public final class Constants {

    // ========== Redis Key 模板 ==========
    /** 短码 -> 原始URL 缓存 */
    public static final String CACHE_KEY_SHORT_CODE = "shortlink:code:%s";
    /** MD5 -> 短码 缓存（防重复生成） */
    public static final String CACHE_KEY_MD5 = "shortlink:md5:%s";

    // ========== Redisson 组件名称 ==========
    /** 布隆过滤器名称 */
    public static final String BLOOM_FILTER_NAME = "shortlink:bloom";
    /** 限流器 Key */
    public static final String RATE_LIMITER_KEY = "shortlink:ratelimit:global";

    // ========== 缓存 TTL ==========
    /** 短码缓存过期时间：7天 */
    public static final long CACHE_TTL_CODE = Duration.ofDays(7).toSeconds();
    /** MD5映射缓存过期时间：30天 */
    public static final long CACHE_TTL_MD5 = Duration.ofDays(30).toSeconds();

    // ========== 业务常量 ==========
    /** Base62 字符集 */
    public static final String BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    /** Base62 进制 */
    public static final int BASE62_RADIX = 62;
    /** 短码生成最大重试次数 */
    public static final int MAX_GENERATE_RETRY = 3;
    /** 有效状态 */
    public static final int STATUS_VALID = 1;
    /** 失效状态 */
    public static final int STATUS_INVALID = 0;

    private Constants() {
    }
}
