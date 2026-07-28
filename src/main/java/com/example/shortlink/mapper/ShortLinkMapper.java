package com.example.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.shortlink.entity.ShortLink;
import org.apache.ibatis.annotations.Mapper;

/**
 * 短链接映射数据访问层
 */
@Mapper
public interface ShortLinkMapper extends BaseMapper<ShortLink> {
}
