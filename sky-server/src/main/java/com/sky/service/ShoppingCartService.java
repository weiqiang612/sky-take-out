package com.sky.service;

import com.sky.dto.ShoppingCartDTO;

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
}
