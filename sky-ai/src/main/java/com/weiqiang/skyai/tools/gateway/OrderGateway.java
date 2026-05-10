package com.weiqiang.skyai.tools.gateway;

public interface OrderGateway {

    String getOrderDetail(String userId, String orderId);

    String listRecentOrders(String userId, int pageSize);

    String cancelOrder(String userId, String orderId);

    String requestRefund(String userId, String orderId, String reason);

    String updateDeliveryAddress(String userId, String orderId, String newAddress);

    String remindOrder(String userId, String orderId);

    String reorder(String userId, String orderId);
}
