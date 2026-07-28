package com.example.shortlink.service.impl;

import com.example.shortlink.entity.AccessLog;
import com.example.shortlink.mapper.AccessLogMapper;
import com.example.shortlink.service.AccessLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 访问日志服务实现（异步记录）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessLogServiceImpl implements AccessLogService {

    private final AccessLogMapper accessLogMapper;

    /** 常用代理头 */
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
    };

    @Override
    @Async("asyncTaskExecutor")
    public void recordAccess(String shortCode, HttpServletRequest request) {
        try {
            AccessLog accessLog = AccessLog.builder()
                    .shortCode(shortCode)
                    .accessIp(getClientIp(request))
                    .userAgent(truncate(request.getHeader("User-Agent"), 512))
                    .referer(truncate(request.getHeader("Referer"), 2048))
                    .accessTime(LocalDateTime.now())
                    .build();
            accessLogMapper.insert(accessLog);
            log.debug("访问日志记录成功: shortCode={}, ip={}", shortCode, accessLog.getAccessIp());
        } catch (Exception e) {
            // 日志记录失败不应影响主流程
            log.error("访问日志记录失败: shortCode={}", shortCode, e);
        }
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For 可能包含多个IP，取第一个
                int index = ip.indexOf(',');
                if (index != -1) {
                    ip = ip.substring(0, index);
                }
                return ip.trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 截断字符串到指定长度
     */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
