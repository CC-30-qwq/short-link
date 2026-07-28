package com.example.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.shortlink.entity.AccessLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 访问日志数据访问层
 */
@Mapper
public interface AccessLogMapper extends BaseMapper<AccessLog> {
}
