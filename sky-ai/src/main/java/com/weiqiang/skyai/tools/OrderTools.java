package com.weiqiang.skyai.tools;

import com.weiqiang.skyai.tools.gateway.OrderGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderTools {

    private final OrderGateway orderGateway;

    @Tool(description = "Get the current user's order details by order id.")
    public String getOrderDetail(@ToolParam(description = "Order id") String orderId, ToolContext context) {
        return orderGateway.getOrderDetail(ToolUser.userId(context), orderId);
    }

    @Tool(description = "List the current user's most recent orders.")
    public String listRecentOrders(@ToolParam(description = "Maximum number of orders to return") int pageSize, ToolContext context) {
        return orderGateway.listRecentOrders(ToolUser.userId(context), pageSize);
    }

    @Tool(description = "Cancel an unpaid or unconfirmed order for the current user after confirmation.")
    public String cancelOrder(@ToolParam(description = "Order id") String orderId, ToolContext context) {
        return orderGateway.cancelOrder(ToolUser.userId(context), orderId);
    }

    @Tool(description = "Request a refund for an order after the user confirms the reason.")
    public String requestRefund(@ToolParam(description = "Order id") String orderId,
                                @ToolParam(description = "Refund reason") String reason,
                                ToolContext context) {
        return orderGateway.requestRefund(ToolUser.userId(context), orderId, reason);
    }

    @Tool(description = "Update the delivery address for an order after the user confirms the new address.")
    public String updateDeliveryAddress(@ToolParam(description = "Order id") String orderId,
                                        @ToolParam(description = "New delivery address") String newAddress,
                                        ToolContext context) {
        return orderGateway.updateDeliveryAddress(ToolUser.userId(context), orderId, newAddress);
    }

    @Tool(description = "Send an order reminder to the shop for the current user's order.")
    public String remindOrder(@ToolParam(description = "Order id") String orderId, ToolContext context) {
        return orderGateway.remindOrder(ToolUser.userId(context), orderId);
    }

    @Tool(description = "Add all items from a previous order back into the current user's cart.")
    public String reorder(@ToolParam(description = "Order id") String orderId, ToolContext context) {
        return orderGateway.reorder(ToolUser.userId(context), orderId);
    }
}
