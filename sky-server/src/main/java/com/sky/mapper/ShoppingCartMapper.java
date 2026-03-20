package com.sky.mapper;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/19 18:04
 */

@Mapper
public interface ShoppingCartMapper {

    /**
     * 根据套餐或菜品ID查询当前用户的商品数量
     * 注意：对不同口味的菜品作了区分
     * @param shoppingCartDTO
     * @param userId
     * @return
     */
    Integer countByDishORSetmealId(@Param("shoppingCartDTO") ShoppingCartDTO shoppingCartDTO,@Param("userId") Long userId);

    /**
     * 添加购物车
     * @param shoppingCart
     */
    void save(ShoppingCart shoppingCart);

    /**
     * 更改当前用户购物车商品的数量，适用于商品数量不为0时，增加和删除商品的情况
     * @param newNumber
     * @param shoppingCartDTO
     * @param userId
     */
    void updateNumberForGoods(@Param("newNumber") Integer newNumber,
                              @Param("shoppingCartDTO") ShoppingCartDTO shoppingCartDTO,
                              @Param("userId") Long userId);
}
