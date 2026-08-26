package com.example.shortlink.service.impl;

import com.example.shortlink.entity.AccessLog;
import com.example.shortlink.mapper.AccessLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AccessLogServiceImpl 单元测试：异步访问日志记录 + IP 提取 + 异常隔离
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessLogServiceImplTest {

    @Mock
    private AccessLogMapper accessLogMapper;
    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AccessLogServiceImpl service;

    @Test
    @DisplayName("正常记录：字段正确，且 X-Forwarded-For 取第一个 IP")
    void recordAccess_insertWithCorrectFields() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 5.6.7.8");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getHeader("Referer")).thenReturn("http://ref.com");

        service.recordAccess("abc", request);

        ArgumentCaptor<AccessLog> captor = ArgumentCaptor.forClass(AccessLog.class);
        verify(accessLogMapper).insert(captor.capture());
        AccessLog log = captor.getValue();
        assertThat(log.getShortCode()).isEqualTo("abc");
        assertThat(log.getAccessIp()).isEqualTo("1.2.3.4");
        assertThat(log.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(log.getReferer()).isEqualTo("http://ref.com");
        assertThat(log.getAccessTime()).isNotNull();
    }

    @Test
    @DisplayName("无代理头时回退到 RemoteAddr")
    void recordAccess_fallbackToRemoteAddr() {
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        service.recordAccess("abc", request);

        ArgumentCaptor<AccessLog> captor = ArgumentCaptor.forClass(AccessLog.class);
        verify(accessLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getAccessIp()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("User-Agent 超长时截断到 512 字符")
    void recordAccess_truncateLongUserAgent() {
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("x".repeat(600));

        service.recordAccess("abc", request);

        ArgumentCaptor<AccessLog> captor = ArgumentCaptor.forClass(AccessLog.class);
        verify(accessLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserAgent()).hasSize(512);
    }

    @Test
    @DisplayName("入库异常不影响主流程（吞掉异常）")
    void recordAccess_swallowInsertException() {
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        doThrow(new RuntimeException("db down")).when(accessLogMapper).insert(any());

        assertThatCode(() -> service.recordAccess("abc", request))
                .doesNotThrowAnyException();
    }
}
