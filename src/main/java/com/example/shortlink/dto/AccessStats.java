package com.example.shortlink.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 访问统计聚合结果（访问次数 + 最近访问时间）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessStats {

    /** 访问次数 */
    private Long accessCount;

    /** 最近访问时间（无访问记录时为 null） */
    private LocalDateTime lastAccessTime;
}
