package com.example.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.shortlink.dto.AccessStats;
import com.example.shortlink.entity.AccessLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 访问日志数据访问层
 */
public interface AccessLogMapper extends BaseMapper<AccessLog> {

    /**
     * 一次性聚合统计某短码的访问次数与最近访问时间。
     * <p>
     * 替代原来「count 一次 + 查最近一条一次」的两次往返，访问日志表越大收益越明显。
     */
    @Select("SELECT COUNT(*) AS accessCount, MAX(access_time) AS lastAccessTime " +
            "FROM t_access_log WHERE short_code = #{shortCode}")
    AccessStats selectAccessStats(@Param("shortCode") String shortCode);
}
