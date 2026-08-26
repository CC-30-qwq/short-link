package com.example.shortlink.service;

import com.example.shortlink.dto.ShortenRequest;
import com.example.shortlink.dto.ShortenResponse;
import com.example.shortlink.dto.StatisticsResponse;

/**
 * 短链接核心业务接口
 */
public interface ShortLinkService {

    /**
     * 生成短链接
     *
     * @param request 生成请求（含原始URL和可选过期时间）
     * @return 短链接信息
     */
    ShortenResponse shorten(ShortenRequest request);

    /**
     * 根据短码获取原始URL（用于跳转）
     *
     * @param shortCode 短链码
     * @return 原始URL
     */
    String getOriginalUrl(String shortCode);

    /**
     * 获取短链访问统计
     *
     * @param shortCode 短链码
     * @return 统计信息
     */
    StatisticsResponse getStatistics(String shortCode);

    /**
     * 使短链失效（软删除，状态置为失效并清理缓存）
     *
     * @param shortCode 短链码
     */
    void invalidate(String shortCode);
}
