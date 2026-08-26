package com.example.shortlink.controller;

import com.example.shortlink.dto.ShortenRequest;
import com.example.shortlink.dto.ShortenResponse;
import com.example.shortlink.dto.StatisticsResponse;
import com.example.shortlink.exception.BusinessException;
import com.example.shortlink.exception.ErrorCode;
import com.example.shortlink.manager.RateLimiterManager;
import com.example.shortlink.mapper.AccessLogMapper;
import com.example.shortlink.mapper.ShortLinkMapper;
import com.example.shortlink.service.AccessLogService;
import com.example.shortlink.service.ShortLinkService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ShortLinkController 接口层测试（@WebMvcTest + mock 依赖，不连数据库/Redis）
 */
@WebMvcTest(ShortLinkController.class)
class ShortLinkControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ShortLinkService shortLinkService;
    @MockBean
    private AccessLogService accessLogService;
    @MockBean
    private RateLimiterManager rateLimiterManager;
    @MockBean
    private ShortLinkMapper shortLinkMapper;
    @MockBean
    private AccessLogMapper accessLogMapper;

    @Test
    @DisplayName("生成短链：正常返回 200 和短链信息")
    void shorten_ok() throws Exception {
        when(rateLimiterManager.tryAcquire()).thenReturn(true);
        when(shortLinkService.shorten(any())).thenReturn(
                ShortenResponse.builder()
                        .shortCode("abc")
                        .shortUrl("http://localhost:8080/abc")
                        .originalUrl("http://example.com")
                        .build());

        ShortenRequest req = new ShortenRequest();
        req.setOriginalUrl("http://example.com");

        mockMvc.perform(post("/api/v1/url/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.shortCode").value("abc"));
    }

    @Test
    @DisplayName("生成短链：触发限流，业务码 429")
    void shorten_rateLimited() throws Exception {
        when(rateLimiterManager.tryAcquire()).thenReturn(false);

        ShortenRequest req = new ShortenRequest();
        req.setOriginalUrl("http://example.com");

        // 业务异常无 @ResponseStatus，HTTP 仍为 200，错误码在 body.code
        mockMvc.perform(post("/api/v1/url/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(429));
    }

    @Test
    @DisplayName("生成短链：参数为空，HTTP 400")
    void shorten_validationError() throws Exception {
        when(rateLimiterManager.tryAcquire()).thenReturn(true);

        ShortenRequest req = new ShortenRequest(); // originalUrl 为空，触发 @NotBlank

        mockMvc.perform(post("/api/v1/url/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("跳转：302 重定向并记录访问日志")
    void redirect_302() throws Exception {
        when(shortLinkService.getOriginalUrl("abc")).thenReturn("http://example.com");

        mockMvc.perform(get("/abc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://example.com"));

        verify(accessLogService).recordAccess(any(), any());
    }

    @Test
    @DisplayName("跳转：短码不存在，业务码 1003")
    void redirect_notFound() throws Exception {
        when(shortLinkService.getOriginalUrl("nope"))
                .thenThrow(new BusinessException(ErrorCode.SHORT_CODE_NOT_FOUND));

        mockMvc.perform(get("/nope"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("统计：正常返回访问次数")
    void statistics_ok() throws Exception {
        when(shortLinkService.getStatistics("abc"))
                .thenReturn(StatisticsResponse.builder()
                        .shortCode("abc")
                        .accessCount(10L)
                        .build());

        mockMvc.perform(get("/api/v1/url/statistics").param("shortCode", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessCount").value(10));
    }

    @Test
    @DisplayName("失效短链：正常返回 200")
    void invalidate_ok() throws Exception {
        mockMvc.perform(delete("/api/v1/url/abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(shortLinkService).invalidate("abc");
    }
}
