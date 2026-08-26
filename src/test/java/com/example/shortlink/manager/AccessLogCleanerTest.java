package com.example.shortlink.manager;

import com.example.shortlink.mapper.AccessLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AccessLogCleaner 单元测试：定时清理过期访问日志
 */
@ExtendWith(MockitoExtension.class)
class AccessLogCleanerTest {

    @Mock
    private AccessLogMapper accessLogMapper;

    @InjectMocks
    private AccessLogCleaner cleaner;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cleaner, "retentionDays", 30);
    }

    @Test
    @DisplayName("清理过期日志：调用 delete 删除阈值之前的记录")
    void cleanExpiredLogs_delete() {
        when(accessLogMapper.delete(any())).thenReturn(10);

        cleaner.cleanExpiredLogs();

        verify(accessLogMapper).delete(any());
    }

    @Test
    @DisplayName("清理异常不影响主流程")
    void cleanExpiredLogs_swallowException() {
        doThrow(new RuntimeException("db down")).when(accessLogMapper).delete(any());

        assertThatCode(() -> cleaner.cleanExpiredLogs()).doesNotThrowAnyException();
    }
}
