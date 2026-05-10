package com.weiqiang.skyai.tools.gateway;

public interface MenuGateway {

    String listCategories(Integer type);

    String listDishesByCategory(Long categoryId);

    String listSetmealsByCategory(Long categoryId);

    String listSetmealDishes(Long setmealId);

    String getShopStatus();
}
