package com.weiqiang.skyai.advisor;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ToolPolicyRegistry {

    private static final Set<String> ALWAYS_VISIBLE_SELF_SERVICE_TOOLS = Set.of(
            "searchOrders",
            "getOrderDetail",
            "listRecentOrders",
            "searchDishes",
            "searchSetmeals",
            "listCategories",
            "listDishesByCategory",
            "listSetmealsByCategory",
            "listSetmealDishes",
            "getShopStatus",
            "searchCartItems",
            "listCart",
            "addDishToCart",
            "addSetmealToCart",
            "removeCartItem",
            "cleanCart",
            "searchAddresses",
            "listAddresses",
            "getDefaultAddress",
            "setDefaultAddress",
            "updateAddress",
            "cancelOrder",
            "requestRefund",
            "updateDeliveryAddress",
            "remindOrder",
            "reorder"
    );

    private ToolPolicyRegistry() {
    }

    public static Set<String> alwaysVisibleSelfServiceTools() {
        return ALWAYS_VISIBLE_SELF_SERVICE_TOOLS;
    }

    public static Set<String> mergeAllowedTools(Set<String> allowedTools) {
        Set<String> merged = new LinkedHashSet<>(ALWAYS_VISIBLE_SELF_SERVICE_TOOLS);
        if (allowedTools != null) {
            merged.addAll(allowedTools);
        }
        return merged;
    }
}
