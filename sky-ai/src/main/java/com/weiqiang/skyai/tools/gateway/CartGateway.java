package com.weiqiang.skyai.tools.gateway;

public interface CartGateway {

    String listCart(String userId);

    String addDishToCart(String userId, Long dishId, String dishFlavor);

    String addSetmealToCart(String userId, Long setmealId);

    String removeCartItem(String userId, Long dishId, Long setmealId, String dishFlavor);

    String cleanCart(String userId);
}
