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

    /**
     * 将Base62字符串解码为原始数字ID
     *
     * @param code Base62编码字符串
     * @return 原始数字ID
     */
    public static long decode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("短码不能为空");
        }

        long result = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            int index = Constants.BASE62_CHARS.indexOf(c);
            if (index == -1) {
                throw new IllegalArgumentException("非法的Base62字符: " + c);
            }
            result = result * Constants.BASE62_RADIX + index;
        }
        return result;
    }
}
