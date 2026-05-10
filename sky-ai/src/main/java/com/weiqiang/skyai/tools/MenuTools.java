package com.weiqiang.skyai.tools;

import com.weiqiang.skyai.tools.gateway.MenuGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MenuTools {

    private final MenuGateway menuGateway;

    @Tool(description = "List menu categories, optionally filtered by dish or setmeal type.")
    public String listCategories(@ToolParam(description = "Category type: 1 for dishes, 2 for setmeals", required = false) Integer type) {
        return menuGateway.listCategories(type);
    }

    @Tool(description = "List available dishes in a menu category.")
    public String listDishesByCategory(@ToolParam(description = "Category id") Long categoryId) {
        return menuGateway.listDishesByCategory(categoryId);
    }

    @Tool(description = "List available setmeals in a menu category.")
    public String listSetmealsByCategory(@ToolParam(description = "Category id") Long categoryId) {
        return menuGateway.listSetmealsByCategory(categoryId);
    }

    @Tool(description = "List the dishes included in a setmeal.")
    public String listSetmealDishes(@ToolParam(description = "Setmeal id") Long setmealId) {
        return menuGateway.listSetmealDishes(setmealId);
    }

    @Tool(description = "Get whether the shop is currently open or closed.")
    public String getShopStatus() {
        return menuGateway.getShopStatus();
    }
}
