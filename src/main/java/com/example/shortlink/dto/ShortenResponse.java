package com.example.shortlink.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 短链生成响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "短链生成响应")
public class ShortenResponse {

    @Schema(description = "短链码", example = "3xY7kP")
    private String shortCode;

    @Schema(description = "完整短链接", example = "http://localhost:8080/3xY7kP")
    private String shortUrl;

    @Schema(description = "原始长URL")
    private String originalUrl;

    @Schema(description = "过期时间（null表示永久有效）")
    private LocalDateTime expireTime;
}
