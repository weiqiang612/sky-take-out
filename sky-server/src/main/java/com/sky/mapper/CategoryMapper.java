package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @author 袁志刚
 * @version 1.0
 * @Date 2026/3/12 17:16
 */

@Mapper
public interface CategoryMapper {
    Page<Category> page(@Param("name") String name, @Param("type") Integer type);
}
