package com.example.shortlink.common;

import com.example.shortlink.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Result 统一响应包装的单元测试
 */
class ResultTest {

    @Test
    @DisplayName("ok(data) 返回成功码、消息和数据")
    void okWithData() {
        Result<String> result = Result.ok("hello");
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).isEqualTo("操作成功");
        assertThat(result.getData()).isEqualTo("hello");
    }

    @Test
    @DisplayName("fail(ErrorCode) 返回对应错误码和消息")
    void failWithErrorCode() {
        Result<Void> result = Result.fail(ErrorCode.URL_INVALID);
        assertThat(result.getCode()).isEqualTo(1001);
        assertThat(result.getMessage()).isEqualTo("URL格式不合法");
        assertThat(result.getData()).isNull();
    }

    @Test
    @DisplayName("fail(code, message) 返回自定义错误")
    void failWithCodeAndMessage() {
        Result<Void> result = Result.fail(500, "服务器内部错误");
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("服务器内部错误");
    }

    @Test
    @DisplayName("fail(ErrorCode, detail) 使用自定义详情")
    void failWithErrorCodeAndDetail() {
        Result<Void> result = Result.fail(ErrorCode.BAD_REQUEST, "字段校验失败");
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).isEqualTo("字段校验失败");
    }
}
