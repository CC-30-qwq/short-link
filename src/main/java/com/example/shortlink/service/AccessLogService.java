package com.example.shortlink.service;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 访问日志服务接口
 */
public interface AccessLogService {

    /**
     * 异步记录访问日志
     *
     * @param shortCode 短链码
     * @param request   HTTP请求（用于提取IP、UA、Referer）
     */
    void recordAccess(String shortCode, HttpServletRequest request);
}
