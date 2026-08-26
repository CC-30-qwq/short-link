package com.example.shortlink.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Base62Encoder 单元测试
 * <p>
 * 字符集顺序: 0-9, A-Z, a-z（共62个），因此 index 10='A', 35='Z', 36='a', 61='z'
 */
class Base62EncoderTest {

    @Test
    @DisplayName("编码 0 返回 '0'")
    void encodeZero() {
        assertThat(Base62Encoder.encode(0L)).isEqualTo("0");
    }

    @Test
    @DisplayName("编码负数抛异常")
    void encodeNegativeShouldThrow() {
        assertThatThrownBy(() -> Base62Encoder.encode(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("负数");
    }

    @Test
    @DisplayName("编码已知值正确")
    void encodeKnownValues() {
        assertThat(Base62Encoder.encode(1L)).isEqualTo("1");
        assertThat(Base62Encoder.encode(10L)).isEqualTo("A");
        assertThat(Base62Encoder.encode(35L)).isEqualTo("Z");
        assertThat(Base62Encoder.encode(36L)).isEqualTo("a");
        assertThat(Base62Encoder.encode(61L)).isEqualTo("z");
        assertThat(Base62Encoder.encode(62L)).isEqualTo("10");
        assertThat(Base62Encoder.encode(63L)).isEqualTo("11");
    }

    @Test
    @DisplayName("连续 10000 个 ID 编码结果互不相同（单射性）")
    void encodeIsInjectiveForRange() {
        Set<String> codes = new HashSet<>();
        for (long i = 0; i < 10_000; i++) {
            codes.add(Base62Encoder.encode(i));
        }
        assertThat(codes).hasSize(10_000);
    }
}
