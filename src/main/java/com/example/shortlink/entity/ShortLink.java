package com.example.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 短链接映射实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_short_link")
public class ShortLink {

    /** 主键ID（雪花算法生成，手动赋值） */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 短链码（Base62编码） */
    private String shortCode;

    /** 原始长URL */
    private String originalUrl;

    /** 原始URL的MD5值（防重复生成） */
    private String originalUrlMd5;

    /** 过期时间（NULL表示永久有效） */
    private LocalDateTime expireTime;

    /** 状态: 1=有效, 0=失效 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
