package com.example.shortlink.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短链生成请求
 */
@Data
@Schema(description = "短链生成请求")
public class ShortenRequest {

    @NotBlank(message = "原始URL不能为空")
    @Size(max = 2048, message = "URL长度不能超过2048个字符")
    @Schema(description = "原始长URL", example = "https://www.example.com/very/long/path?param=1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String originalUrl;

    @Schema(description = "过期时间（不填则永久有效）", example = "2025-12-31 23:59:59")
    private LocalDateTime expireTime;
}
