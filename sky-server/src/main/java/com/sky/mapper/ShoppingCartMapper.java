package com.sky.mapper;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/19 18:04
 */

@Mapper
public interface ShoppingCartMapper {

    /**
     * 添加购物车
     * @param shoppingCart
     */
    void save(ShoppingCart shoppingCart);

    /**
     * 更改当前用户购物车商品的数量，适用于商品数量不为0时，增加和删除商品的情况
     * @param newNumber
     * @param id 购物车id
     */
    @Update("update shopping_cart set number = #{newNumber} where id = #{id}")
    void updateNumberForGoods(@Param("newNumber") Integer newNumber,
                              @Param("id") Long id);

    /**
     * 查询购物车
     * @param shoppingCart1
     * @return
     */
    List<ShoppingCart> list(ShoppingCart shoppingCart1);

    /**
     * 删除购物车商品
     * @param shoppingCart
     */
    void remove(ShoppingCart shoppingCart);

    /**
     * 批量添加购物车
     * @param shoppingCarts
     */
    void saveBatch(List<ShoppingCart> shoppingCarts);
}
