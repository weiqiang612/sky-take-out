package com.weiqiang.skyai.tools;

import com.weiqiang.skyai.tools.gateway.CartGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CartTools {

    private final CartGateway cartGateway;

    @Tool(description = "List all items in the current user's shopping cart.")
    public String listCart(ToolContext context) {
        return cartGateway.listCart(ToolUser.userId(context));
    }

    @Tool(description = "Add one dish to the current user's shopping cart.")
    public String addDishToCart(@ToolParam(description = "Dish id") Long dishId,
                                @ToolParam(description = "Dish flavor", required = false) String dishFlavor,
                                ToolContext context) {
        return cartGateway.addDishToCart(ToolUser.userId(context), dishId, dishFlavor);
    }

    @Tool(description = "Add one setmeal to the current user's shopping cart.")
    public String addSetmealToCart(@ToolParam(description = "Setmeal id") Long setmealId, ToolContext context) {
        return cartGateway.addSetmealToCart(ToolUser.userId(context), setmealId);
    }

    @Tool(description = "Remove one matching dish or setmeal item from the current user's shopping cart.")
    public String removeCartItem(@ToolParam(description = "Dish id", required = false) Long dishId,
                                 @ToolParam(description = "Setmeal id", required = false) Long setmealId,
                                 @ToolParam(description = "Dish flavor", required = false) String dishFlavor,
                                 ToolContext context) {
        return cartGateway.removeCartItem(ToolUser.userId(context), dishId, setmealId, dishFlavor);
    }

    @Tool(description = "Clear all items from the current user's shopping cart after confirmation.")
    public String cleanCart(ToolContext context) {
        return cartGateway.cleanCart(ToolUser.userId(context));
    }
}
