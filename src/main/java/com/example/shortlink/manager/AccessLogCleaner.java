package com.example.shortlink.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.shortlink.entity.AccessLog;
import com.example.shortlink.mapper.AccessLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 访问日志定时清理：定期删除过期的访问日志，避免 t_access_log 无限增长
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLogCleaner {

    private final AccessLogMapper accessLogMapper;

    @Value("${shortlink.access-log.retention-days:30}")
    private int retentionDays;

    /**
     * 每天凌晨 3 点清理 retentionDays 天前的访问日志（失败不影响主流程）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredLogs() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
            LambdaQueryWrapper<AccessLog> wrapper = new LambdaQueryWrapper<>();
            wrapper.lt(AccessLog::getAccessTime, threshold);
            int deleted = accessLogMapper.delete(wrapper);
            log.info("访问日志清理完成：删除 {} 天前的记录 {} 条", retentionDays, deleted);
        } catch (Exception e) {
            log.warn("访问日志清理失败（不影响主流程）: {}", e.getMessage());
        }
    }
}
