-- =====================================================
-- 短链接生成平台 - 数据库初始化脚本
-- =====================================================

CREATE TABLE IF NOT EXISTS t_short_link (
    id               BIGINT        NOT NULL COMMENT '雪花算法主键ID',
    short_code       VARCHAR(12)   NOT NULL COMMENT 'Base62编码生成的短链码',
    original_url     VARCHAR(2048) NOT NULL COMMENT '原始长URL',
    original_url_md5 CHAR(32)      NOT NULL COMMENT '原始URL的MD5值(用于防重)',
    expire_time      DATETIME      DEFAULT NULL COMMENT '过期时间，NULL表示永不过期',
    status           TINYINT       NOT NULL DEFAULT 1 COMMENT '状态: 1=有效 0=失效',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_short_code (short_code),
    UNIQUE KEY uk_md5 (original_url_md5),
    KEY idx_status (status),
    KEY idx_expire_time (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='短链接映射表';

CREATE TABLE IF NOT EXISTS t_access_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    short_code   VARCHAR(12)  NOT NULL COMMENT '短链码',
    access_ip    VARCHAR(45)  DEFAULT NULL COMMENT '访问者IP(IPv6可达45字符)',
    user_agent   VARCHAR(512) DEFAULT NULL COMMENT '浏览器User-Agent',
    referer      VARCHAR(2048) DEFAULT NULL COMMENT 'HTTP Referer',
    access_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
    PRIMARY KEY (id),
    KEY idx_short_code (short_code),
    KEY idx_access_time (access_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='访问日志表';
