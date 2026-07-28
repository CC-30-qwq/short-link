package com.example.shortlink.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 短链统计响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "短链统计响应")
public class StatisticsResponse {

    @Schema(description = "短链码")
    private String shortCode;

    @Schema(description = "完整短链接")
    private String shortUrl;

    @Schema(description = "原始长URL")
    private String originalUrl;

    @Schema(description = "总访问次数")
    private Long accessCount;

    @Schema(description = "最近访问时间")
    private LocalDateTime lastAccessTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "过期时间（null表示永久有效）")
    private LocalDateTime expireTime;

    @Schema(description = "状态: 1=有效, 0=失效")
    private Integer status;
}
