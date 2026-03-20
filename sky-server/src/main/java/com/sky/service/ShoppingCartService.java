package com.sky.service;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;

import java.util.List;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/19 17:58
 */


public interface ShoppingCartService {
    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    void save(ShoppingCartDTO shoppingCartDTO);

    /**
     * 查看购物车
     * @param id 用户ID
     * @return
     */
    List<ShoppingCart> list(Long id);

    /**
     * 删除购物车商品
     * @param shoppingCartDTO
     */
    void remove(ShoppingCartDTO shoppingCartDTO);

    /**
     * 清空购物车
     */
    void clean();
}
