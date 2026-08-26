package com.example.shortlink.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.shortlink.common.Constants;
import com.example.shortlink.dto.AccessStats;
import com.example.shortlink.dto.ShortenRequest;
import com.example.shortlink.dto.ShortenResponse;
import com.example.shortlink.dto.StatisticsResponse;
import com.example.shortlink.entity.ShortLink;
import com.example.shortlink.exception.BusinessException;
import com.example.shortlink.exception.ErrorCode;
import com.example.shortlink.manager.BloomFilterManager;
import com.example.shortlink.manager.CacheManager;
import com.example.shortlink.mapper.AccessLogMapper;
import com.example.shortlink.mapper.ShortLinkMapper;
import com.example.shortlink.util.Base62Encoder;
import com.example.shortlink.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;

/**
 * 短链接核心业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements com.example.shortlink.service.ShortLinkService {

    private final ShortLinkMapper shortLinkMapper;
    private final AccessLogMapper accessLogMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final CacheManager cacheManager;
    private final BloomFilterManager bloomFilterManager;

    @Value("${shortlink.domain}")
    private String domain;

    /**
     * 短码生成结果（内部类，避免重复编解码）
     */
    private record ShortCodeResult(long id, String shortCode) {
    }

    @Override
    public ShortenResponse shorten(ShortenRequest request) {
        // 1. 规范化 URL（补齐协议前缀）
        String originalUrl = normalizeUrl(request.getOriginalUrl());

        // 2. 计算 MD5（防重）
        String md5 = DigestUtil.md5Hex(originalUrl);

        // 3. 检查 Redis 缓存：此 URL 是否已经生成过短码
        // 缓存里只存永久有效的短码，故命中即代表永久短码、无过期问题
        String cachedShortCode = cacheManager.getShortCodeByMd5(md5);
        if (cachedShortCode != null) {
            log.info("命中MD5缓存，直接返回已有短码: url={}, shortCode={}", originalUrl, cachedShortCode);
            return buildResponse(cachedShortCode, originalUrl, null);
        }

        // 4. 查询数据库：此 URL 是否已存在
        LambdaQueryWrapper<ShortLink> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ShortLink::getOriginalUrlMd5, md5)
                .eq(ShortLink::getStatus, Constants.STATUS_VALID);
        ShortLink existingLink = shortLinkMapper.selectOne(queryWrapper);
        if (existingLink != null) {
            // 只有永久短链才回填缓存，避免过期短链命中缓存后绕过过期校验
            if (existingLink.getExpireTime() == null) {
                cacheManager.cacheMd5(md5, existingLink.getShortCode());
                cacheManager.cacheShortCode(existingLink.getShortCode(), existingLink.getOriginalUrl());
            }
            log.info("URL已存在，返回已有短码: url={}, shortCode={}", originalUrl, existingLink.getShortCode());
            return buildResponse(existingLink.getShortCode(), originalUrl, existingLink.getExpireTime());
        }

        // 5. 生成新的短码（带重试机制，防 Base62 碰撞）
        ShortCodeResult result = generateShortCodeWithRetry();

        // 6. 构建实体并入库（直接使用雪花ID作为主键，避免解码）
        ShortLink shortLink = ShortLink.builder()
                .id(result.id())
                .shortCode(result.shortCode())
                .originalUrl(originalUrl)
                .originalUrlMd5(md5)
                .expireTime(request.getExpireTime())
                .status(Constants.STATUS_VALID)
                .build();

        shortLinkMapper.insert(shortLink);

        // 7. 写入缓存（只有永久短链才缓存；有过期时间的短链每次实时查库校验过期）
        if (shortLink.getExpireTime() == null) {
            cacheManager.cacheShortCode(result.shortCode(), originalUrl);
            cacheManager.cacheMd5(md5, result.shortCode());
        }

        // 8. 加入布隆过滤器
        bloomFilterManager.add(result.shortCode());

        log.info("短链生成成功: url={}, shortCode={}",
                originalUrl.substring(0, Math.min(80, originalUrl.length())), result.shortCode());
        return buildResponse(result.shortCode(), originalUrl, request.getExpireTime());
    }

    @Override
    public String getOriginalUrl(String shortCode) {
        // 1. 布隆过滤器快速判断（不存在则直接返回404）
        if (!bloomFilterManager.mightContain(shortCode)) {
            log.warn("布隆过滤器判断短码不存在: {}", shortCode);
            throw new BusinessException(ErrorCode.SHORT_CODE_NOT_FOUND);
        }

        // 2. 查询 Redis 缓存
        String originalUrl = cacheManager.getOriginalUrl(shortCode);
        if (originalUrl != null) {
            log.debug("缓存命中: shortCode={}", shortCode);
            return originalUrl;
        }

        // 3. 缓存未命中，查数据库
        LambdaQueryWrapper<ShortLink> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ShortLink::getShortCode, shortCode)
                .eq(ShortLink::getStatus, Constants.STATUS_VALID);
        ShortLink shortLink = shortLinkMapper.selectOne(queryWrapper);

        if (shortLink == null) {
            log.warn("短码不存在: {}", shortCode);
            throw new BusinessException(ErrorCode.SHORT_CODE_NOT_FOUND);
        }

        // 4. 检查是否过期（过期则懒更新状态为失效）
        if (shortLink.getExpireTime() != null && shortLink.getExpireTime().isBefore(LocalDateTime.now())) {
            log.warn("短码已过期，置为失效: shortCode={}, expireTime={}", shortCode, shortLink.getExpireTime());
            markExpired(shortLink.getId());
            throw new BusinessException(ErrorCode.SHORT_CODE_NOT_FOUND);
        }

        // 5. 回填缓存（只有永久短链才缓存）
        if (shortLink.getExpireTime() == null) {
            cacheManager.cacheShortCode(shortCode, shortLink.getOriginalUrl());
            log.debug("缓存回填: shortCode={}", shortCode);
        }

        return shortLink.getOriginalUrl();
    }

    @Override
    public StatisticsResponse getStatistics(String shortCode) {
        // 1. 查询短链元数据
        LambdaQueryWrapper<ShortLink> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ShortLink::getShortCode, shortCode)
                .eq(ShortLink::getStatus, Constants.STATUS_VALID);
        ShortLink shortLink = shortLinkMapper.selectOne(queryWrapper);

        if (shortLink == null) {
            throw new BusinessException(ErrorCode.SHORT_CODE_NOT_FOUND);
        }

        // 2. 一次性聚合统计访问次数与最近访问时间（一条 SQL，避免两次往返）
        AccessStats stats = accessLogMapper.selectAccessStats(shortCode);
        long accessCount = (stats != null && stats.getAccessCount() != null) ? stats.getAccessCount() : 0L;
        LocalDateTime lastAccessTime = (stats != null) ? stats.getLastAccessTime() : null;

        return StatisticsResponse.builder()
                .shortCode(shortCode)
                .shortUrl(domain + "/" + shortCode)
                .originalUrl(shortLink.getOriginalUrl())
                .accessCount(accessCount)
                .lastAccessTime(lastAccessTime)
                .createTime(shortLink.getCreateTime())
                .expireTime(shortLink.getExpireTime())
                .status(shortLink.getStatus())
                .build();
    }

    @Override
    public void invalidate(String shortCode) {
        // 1. 查库确认存在且有效
        LambdaQueryWrapper<ShortLink> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ShortLink::getShortCode, shortCode)
                .eq(ShortLink::getStatus, Constants.STATUS_VALID);
        ShortLink link = shortLinkMapper.selectOne(queryWrapper);
        if (link == null) {
            throw new BusinessException(ErrorCode.SHORT_CODE_NOT_FOUND);
        }

        // 2. 置失效（软删除）
        ShortLink update = new ShortLink();
        update.setId(link.getId());
        update.setStatus(Constants.STATUS_INVALID);
        shortLinkMapper.updateById(update);

        // 3. 清缓存（布隆过滤器不支持删除，但查库时 status=失效 会自然拦截）
        cacheManager.evictShortCode(shortCode);
        cacheManager.evictMd5(link.getOriginalUrlMd5());

        log.info("短链已失效: shortCode={}", shortCode);
    }

    /**
     * 懒更新：把已过期短链的状态置为失效（失败不影响主流程）
     */
    private void markExpired(Long id) {
        try {
            ShortLink update = new ShortLink();
            update.setId(id);
            update.setStatus(Constants.STATUS_INVALID);
            shortLinkMapper.updateById(update);
        } catch (Exception e) {
            log.warn("更新过期短链状态失败: id={}", id, e);
        }
    }

    /**
     * 带重试的短码生成（同时返回数字ID和短码，避免重复编解码）
     * <p>
     * 碰撞概率极低 (~1/10^18)，重试仅为理论兜底
     */
    private ShortCodeResult generateShortCodeWithRetry() {
        for (int i = 0; i < Constants.MAX_GENERATE_RETRY; i++) {
            long id = snowflakeIdGenerator.nextId();
            String shortCode = Base62Encoder.encode(id);

            // 检查短码唯一性
            LambdaQueryWrapper<ShortLink> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ShortLink::getShortCode, shortCode);
            if (shortLinkMapper.selectCount(queryWrapper) == 0) {
                return new ShortCodeResult(id, shortCode);
            }
            log.warn("短码碰撞（极低概率），重试 {}/{}: shortCode={}", i + 1, Constants.MAX_GENERATE_RETRY, shortCode);
        }
        throw new BusinessException(ErrorCode.SHORT_CODE_GENERATE_FAILED);
    }

    /**
     * 规范化URL：自动补全协议前缀
     */
    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(ErrorCode.URL_INVALID);
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://" + trimmed;
        }
        // 长度校验
        if (trimmed.length() > 2048) {
            throw new BusinessException(ErrorCode.URL_INVALID);
        }
        // 使用 java.net.URI 严格校验：必须是合法的 http/https URL 且带有效 host
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.URL_INVALID);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new BusinessException(ErrorCode.URL_INVALID);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BusinessException(ErrorCode.URL_INVALID);
        }
        return trimmed;
    }

    /**
     * 构建响应对象
     */
    private ShortenResponse buildResponse(String shortCode, String originalUrl, LocalDateTime expireTime) {
        return ShortenResponse.builder()
                .shortCode(shortCode)
                .shortUrl(domain + "/" + shortCode)
                .originalUrl(originalUrl)
                .expireTime(expireTime)
                .build();
    }
}
