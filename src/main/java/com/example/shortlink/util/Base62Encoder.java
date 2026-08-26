package com.example.shortlink.util;

import com.example.shortlink.common.Constants;

/**
 * Base62 编解码工具
 * <p>
 * 字符集: 0-9, A-Z, a-z (共62个字符)
 * <p>
 * 用途: 将雪花算法生成的数字ID转换为短字符串形式的短码
 */
public final class Base62Encoder {

    private Base62Encoder() {
    }

    /**
     * 将数字ID编码为Base62字符串
     *
     * @param id 数字ID
     * @return Base62编码字符串
     */
    public static String encode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("ID不能为负数: " + id);
        }
        if (id == 0) {
            return "0";
        }

        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            int remainder = (int) (id % Constants.BASE62_RADIX);
            sb.append(Constants.BASE62_CHARS.charAt(remainder));
            id /= Constants.BASE62_RADIX;
        }
        // 反转得到正确的顺序（高位在前）
        return sb.reverse().toString();
    }
}
