package com.example.shortlink.controller;

import com.example.shortlink.common.Result;
import com.example.shortlink.dto.ShortenRequest;
import com.example.shortlink.dto.ShortenResponse;
import com.example.shortlink.dto.StatisticsResponse;
import com.example.shortlink.exception.BusinessException;
import com.example.shortlink.exception.ErrorCode;
import com.example.shortlink.manager.RateLimiterManager;
import com.example.shortlink.service.AccessLogService;
import com.example.shortlink.service.ShortLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "短链接服务", description = "短链接生成、跳转与统计接口")
public class ShortLinkController {

    private final ShortLinkService shortLinkService;
    private final AccessLogService accessLogService;
    private final RateLimiterManager rateLimiterManager;

    @GetMapping("/")
    public void index(HttpServletResponse response) throws IOException {
        response.sendRedirect("/index.html");
    }

    @PostMapping("/api/v1/url/shorten")
    @Operation(summary = "生成短链接", description = "接收长URL，返回对应的短链码和完整短链地址")
    public Result<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        if (!rateLimiterManager.tryAcquire()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        ShortenResponse response = shortLinkService.shorten(request);
        return Result.ok(response);
    }

    @GetMapping("/{shortCode:[a-zA-Z0-9]+}")
    @Operation(summary = "短链接跳转", description = "根据短码302重定向至原始长URL，同时异步记录访问日志")
    public void redirect(
            @Parameter(description = "短链码（仅限字母和数字）", required = true)
            @PathVariable String shortCode,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String originalUrl = shortLinkService.getOriginalUrl(shortCode);
        accessLogService.recordAccess(shortCode, request);
        response.sendRedirect(originalUrl);
        log.debug("302重定向: {} -> {}", shortCode, originalUrl.substring(0, Math.min(100, originalUrl.length())));
    }

    @GetMapping("/api/v1/url/statistics")
    @Operation(summary = "获取短链访问统计", description = "根据短码查询该短链接的访问总数、最近访问时间等统计信息")
    public Result<StatisticsResponse> statistics(
            @Parameter(description = "短链码", required = true)
            @RequestParam String shortCode) {
        StatisticsResponse response = shortLinkService.getStatistics(shortCode);
        return Result.ok(response);
    }
}
