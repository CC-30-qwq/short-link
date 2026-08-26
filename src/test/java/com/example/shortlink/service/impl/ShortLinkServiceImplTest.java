package com.example.shortlink.service.impl;

import com.example.shortlink.common.Constants;
import com.example.shortlink.dto.AccessStats;
import com.example.shortlink.dto.ShortenRequest;
import com.example.shortlink.dto.ShortenResponse;
import com.example.shortlink.entity.ShortLink;
import com.example.shortlink.exception.BusinessException;
import com.example.shortlink.exception.ErrorCode;
import com.example.shortlink.manager.BloomFilterManager;
import com.example.shortlink.manager.CacheManager;
import com.example.shortlink.mapper.AccessLogMapper;
import com.example.shortlink.mapper.ShortLinkMapper;
import com.example.shortlink.util.Base62Encoder;
import com.example.shortlink.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ShortLinkServiceImpl 单元测试（Mockito mock 全部依赖，不启动 Spring 容器）
 */
@ExtendWith(MockitoExtension.class)
class ShortLinkServiceImplTest {

    @Mock
    private ShortLinkMapper shortLinkMapper;
    @Mock
    private AccessLogMapper accessLogMapper;
    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private BloomFilterManager bloomFilterManager;

    @InjectMocks
    private ShortLinkServiceImpl service;

    @BeforeEach
    void setUp() {
        // domain 是 @Value 注入字段，不参与构造器注入，需手动设置
        ReflectionTestUtils.setField(service, "domain", "http://localhost:8080");
    }

    private ShortenRequest request(String url) {
        ShortenRequest req = new ShortenRequest();
        req.setOriginalUrl(url);
        return req;
    }

    // ==================== shorten：URL 规范化 ====================

    @Test
    @DisplayName("URL为空时抛 URL_INVALID")
    void shorten_throwWhenUrlBlank() {
        assertThatThrownBy(() -> service.shorten(request("  ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.URL_INVALID.getMessage());
    }

    @Test
    @DisplayName("URL含非法字符（空格）时抛 URL_INVALID")
    void shorten_throwWhenInvalidUrl() {
        assertThatThrownBy(() -> service.shorten(request("http://exa mple.com")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.URL_INVALID.getMessage());
    }

    @Test
    @DisplayName("URL缺少host时抛 URL_INVALID")
    void shorten_throwWhenNoHost() {
        assertThatThrownBy(() -> service.shorten(request("http:///path-only")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.URL_INVALID.getMessage());
    }

    // ==================== shorten：防重三分支 ====================

    @Test
    @DisplayName("命中MD5缓存直接返回，不查库不入库")
    void shorten_returnCachedWhenMd5Hit() {
        when(cacheManager.getShortCodeByMd5(anyString())).thenReturn("abc123");

        ShortenResponse resp = service.shorten(request("example.com"));

        assertThat(resp.getShortCode()).isEqualTo("abc123");
        assertThat(resp.getShortUrl()).isEqualTo("http://localhost:8080/abc123");
        verify(shortLinkMapper, never()).selectOne(any());
        verify(shortLinkMapper, never()).insert(any());
    }

    @Test
    @DisplayName("URL已在库中，回填缓存并返回已有短码")
    void shorten_returnExistingWhenDbHasUrl() {
        ShortLink existing = ShortLink.builder()
                .id(1L).shortCode("xyz789").originalUrl("http://example.com").build();
        when(shortLinkMapper.selectOne(any())).thenReturn(existing);

        ShortenResponse resp = service.shorten(request("example.com"));

        assertThat(resp.getShortCode()).isEqualTo("xyz789");
        verify(cacheManager).cacheMd5(anyString(), eq("xyz789"));
        verify(cacheManager).cacheShortCode(eq("xyz789"), anyString());
        verify(shortLinkMapper, never()).insert(any());
    }

    @Test
    @DisplayName("全新URL生成短码并入库、写缓存、加布隆")
    void shorten_generateNew() {
        when(snowflakeIdGenerator.nextId()).thenReturn(100L);
        when(shortLinkMapper.selectCount(any())).thenReturn(0L);

        ShortenResponse resp = service.shorten(request("example.com"));

        assertThat(resp.getShortCode()).isEqualTo(Base62Encoder.encode(100L));
        verify(shortLinkMapper).insert(any(ShortLink.class));
        verify(cacheManager).cacheShortCode(anyString(), anyString());
        verify(cacheManager).cacheMd5(anyString(), anyString());
        verify(bloomFilterManager).add(anyString());
    }

    @Test
    @DisplayName("生成带过期时间的短链时不写缓存（每次实时查库校验过期）")
    void shorten_newWithExpireTime_doesNotCache() {
        LocalDateTime expire = LocalDateTime.now().plusDays(1);
        ShortenRequest req = request("example.com");
        req.setExpireTime(expire);

        when(snowflakeIdGenerator.nextId()).thenReturn(100L);
        when(shortLinkMapper.selectCount(any())).thenReturn(0L);

        ShortenResponse resp = service.shorten(req);

        assertThat(resp.getShortCode()).isEqualTo(Base62Encoder.encode(100L));
        assertThat(resp.getExpireTime()).isEqualTo(expire);
        verify(shortLinkMapper).insert(any(ShortLink.class));
        verify(cacheManager, never()).cacheShortCode(anyString(), anyString());
        verify(cacheManager, never()).cacheMd5(anyString(), anyString());
        verify(bloomFilterManager).add(anyString());
    }

    @Test
    @DisplayName("URL已存在但有过期时间：不回填缓存，返回已有短链的过期时间")
    void shorten_existingWithExpireTime_notCache() {
        LocalDateTime expire = LocalDateTime.now().plusDays(1);
        ShortLink existing = ShortLink.builder()
                .id(1L).shortCode("xyz789").originalUrl("http://example.com")
                .expireTime(expire).build();
        when(shortLinkMapper.selectOne(any())).thenReturn(existing);

        ShortenResponse resp = service.shorten(request("example.com"));

        assertThat(resp.getShortCode()).isEqualTo("xyz789");
        assertThat(resp.getExpireTime()).isEqualTo(expire);
        verify(cacheManager, never()).cacheMd5(anyString(), anyString());
        verify(cacheManager, never()).cacheShortCode(anyString(), anyString());
        verify(shortLinkMapper, never()).insert(any());
    }

    // ==================== getOriginalUrl：查跳转五分支 ====================

    @Test
    @DisplayName("布隆过滤器判断不存在时抛 SHORT_CODE_NOT_FOUND")
    void getOriginalUrl_throwWhenBloomMiss() {
        when(bloomFilterManager.mightContain("nope")).thenReturn(false);

        assertThatThrownBy(() -> service.getOriginalUrl("nope"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SHORT_CODE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("缓存命中直接返回，不查库")
    void getOriginalUrl_returnFromCache() {
        when(bloomFilterManager.mightContain("abc")).thenReturn(true);
        when(cacheManager.getOriginalUrl("abc")).thenReturn("http://example.com");

        assertThat(service.getOriginalUrl("abc")).isEqualTo("http://example.com");
        verify(shortLinkMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("库中不存在时抛 SHORT_CODE_NOT_FOUND")
    void getOriginalUrl_throwWhenDbNull() {
        when(bloomFilterManager.mightContain("abc")).thenReturn(true);
        when(shortLinkMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getOriginalUrl("abc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SHORT_CODE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("短码已过期时抛 SHORT_CODE_NOT_FOUND 并懒更新状态")
    void getOriginalUrl_throwWhenExpired() {
        when(bloomFilterManager.mightContain("abc")).thenReturn(true);
        ShortLink expired = ShortLink.builder()
                .id(1L)
                .shortCode("abc")
                .expireTime(LocalDateTime.now().minusDays(1))
                .build();
        when(shortLinkMapper.selectOne(any())).thenReturn(expired);

        assertThatThrownBy(() -> service.getOriginalUrl("abc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SHORT_CODE_NOT_FOUND.getMessage());
        verify(shortLinkMapper).updateById(any(ShortLink.class));
    }

    @Test
    @DisplayName("库中短码已过期：不回填缓存并抛 NOT_FOUND")
    void getOriginalUrl_expired_doesNotBackfillCache() {
        when(bloomFilterManager.mightContain("abc")).thenReturn(true);
        ShortLink expired = ShortLink.builder()
                .shortCode("abc").originalUrl("http://example.com")
                .expireTime(LocalDateTime.now().minusDays(1))
                .build();
        when(shortLinkMapper.selectOne(any())).thenReturn(expired);

        assertThatThrownBy(() -> service.getOriginalUrl("abc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SHORT_CODE_NOT_FOUND.getMessage());
        verify(cacheManager, never()).cacheShortCode(anyString(), anyString());
    }

    @Test
    @DisplayName("缓存未命中查库，回填缓存并返回")
    void getOriginalUrl_returnAndBackfill() {
        when(bloomFilterManager.mightContain("abc")).thenReturn(true);
        ShortLink link = ShortLink.builder()
                .shortCode("abc").originalUrl("http://example.com").build();
        when(shortLinkMapper.selectOne(any())).thenReturn(link);

        assertThat(service.getOriginalUrl("abc")).isEqualTo("http://example.com");
        verify(cacheManager).cacheShortCode("abc", "http://example.com");
    }

    // ==================== getStatistics ====================

    @Test
    @DisplayName("统计接口返回访问次数与短链信息")
    void getStatistics_ok() {
        ShortLink link = ShortLink.builder()
                .id(1L).shortCode("abc").originalUrl("http://example.com")
                .createTime(LocalDateTime.now()).status(Constants.STATUS_VALID).build();
        when(shortLinkMapper.selectOne(any())).thenReturn(link);
        when(accessLogMapper.selectAccessStats(anyString())).thenReturn(new AccessStats(5L, null));

        var resp = service.getStatistics("abc");

        assertThat(resp.getShortCode()).isEqualTo("abc");
        assertThat(resp.getAccessCount()).isEqualTo(5L);
        assertThat(resp.getShortUrl()).isEqualTo("http://localhost:8080/abc");
    }

    @Test
    @DisplayName("统计接口：无访问记录时返回 0 次")
    void getStatistics_zeroWhenNoAccess() {
        ShortLink link = ShortLink.builder()
                .id(1L).shortCode("abc").originalUrl("http://example.com")
                .createTime(LocalDateTime.now()).status(Constants.STATUS_VALID).build();
        when(shortLinkMapper.selectOne(any())).thenReturn(link);
        when(accessLogMapper.selectAccessStats(anyString())).thenReturn(new AccessStats(0L, null));

        var resp = service.getStatistics("abc");

        assertThat(resp.getAccessCount()).isEqualTo(0L);
        assertThat(resp.getLastAccessTime()).isNull();
    }

    // ==================== invalidate：失效短链 ====================

    @Test
    @DisplayName("失效短链：置失效并清理短码与MD5缓存")
    void invalidate_ok() {
        ShortLink link = ShortLink.builder()
                .id(1L).shortCode("abc").originalUrlMd5("md5hash").build();
        when(shortLinkMapper.selectOne(any())).thenReturn(link);

        service.invalidate("abc");

        verify(shortLinkMapper).updateById(any(ShortLink.class));
        verify(cacheManager).evictShortCode("abc");
        verify(cacheManager).evictMd5("md5hash");
    }

    @Test
    @DisplayName("失效不存在的短链抛 NOT_FOUND")
    void invalidate_throwWhenNotFound() {
        when(shortLinkMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.invalidate("nope"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SHORT_CODE_NOT_FOUND.getMessage());
    }
}
