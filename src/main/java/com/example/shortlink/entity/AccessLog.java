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
 * 访问日志实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_access_log")
public class AccessLog {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 短链码 */
    private String shortCode;

    /** 访问者IP */
    private String accessIp;

    /** 浏览器User-Agent */
    private String userAgent;

    /** HTTP Referer */
    private String referer;

    /** 访问时间 */
    private LocalDateTime accessTime;
}
