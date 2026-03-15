package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
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

    /**
     * 按需修改套餐中菜品数据
     * @param name
     * @param price
     * @Param dishId
     */
    void updateDishData(@Param("name") String name,@Param("price") BigDecimal price,@Param("dishId") Long dishId);
}
