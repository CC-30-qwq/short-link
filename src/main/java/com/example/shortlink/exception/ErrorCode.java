package com.example.shortlink.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码枚举
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ========== 通用 ==========
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // ========== 业务 ==========
    URL_INVALID(1001, "URL格式不合法"),
    SHORT_CODE_GENERATE_FAILED(1002, "短链码生成失败，请重试"),
    SHORT_CODE_NOT_FOUND(1003, "短链不存在或已失效"),
    ;

    private final int code;
    private final String message;
}
