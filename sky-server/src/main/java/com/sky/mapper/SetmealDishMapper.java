package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/14 14:28
 */

@Mapper
public interface SetmealDishMapper {

    /**
     * 查询多条菜品的关联数量
     * @param ids
     * @return
     */
    Integer queryRelatedDishes(List<Long> ids);
}
