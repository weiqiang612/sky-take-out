package com.weiqiang.skyai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.tools.gateway.OrderGateway;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ToolSearchFormatter searchFormatter = new ToolSearchFormatter(objectMapper);

    @Test
    void requestRefundResolvesOrderNumberBeforeCallingGateway() {
        OrderGateway orderGateway = mock(OrderGateway.class);
        stubRecentOrders(orderGateway);
        when(orderGateway.requestRefund("1", "102", "餐品有误")).thenReturn("OK");
        OrderTools tools = new OrderTools(orderGateway, objectMapper, searchFormatter);

        String result = tools.requestRefund("B20240502", "餐品有误", toolContext("1"));

        assertEquals("OK", result);
        verify(orderGateway).requestRefund("1", "102", "餐品有误");
    }

    @Test
    void cancelOrderFallsBackToInternalIdMatch() {
        OrderGateway orderGateway = mock(OrderGateway.class);
        stubRecentOrders(orderGateway);
        when(orderGateway.cancelOrder("1", "102")).thenReturn("OK");
        OrderTools tools = new OrderTools(orderGateway, objectMapper, searchFormatter);

        String result = tools.cancelOrder("102", toolContext("1"));

        assertEquals("OK", result);
        verify(orderGateway).cancelOrder("1", "102");
    }

    @Test
    void remindOrderReturnsClearMessageWhenOrderCannotBeResolved() {
        OrderGateway orderGateway = mock(OrderGateway.class);
        stubRecentOrders(orderGateway);
        OrderTools tools = new OrderTools(orderGateway, objectMapper, searchFormatter);

        String result = tools.remindOrder("NOPE", toolContext("1"));

        assertTrue(result.contains("未找到对应订单"));
        verify(orderGateway, never()).remindOrder(anyString(), anyString());
    }

    @Test
    void requestRefundRejectsAmbiguousOrderNumberMatches() {
        OrderGateway orderGateway = mock(OrderGateway.class);
        when(orderGateway.listRecentOrders("1", 10)).thenReturn("""
                {"records":[
                  {"id":101,"number":"A20240501"},
                  {"id":102,"number":"A20240501"}
                ]}
                """);
        OrderTools tools = new OrderTools(orderGateway, objectMapper, searchFormatter);

        String result = tools.requestRefund("A20240501", "餐品有误", toolContext("1"));

        assertTrue(result.contains("未找到对应订单"));
        verify(orderGateway, never()).requestRefund(anyString(), anyString(), anyString());
    }

    @Test
    void getOrderDetailResolvesOrderNumberBeforeCallingGateway() {
        OrderGateway orderGateway = mock(OrderGateway.class);
        stubRecentOrders(orderGateway);
        when(orderGateway.getOrderDetail("1", "102")).thenReturn("DETAIL");
        OrderTools tools = new OrderTools(orderGateway, objectMapper, searchFormatter);

        String result = tools.getOrderDetail("B20240502", toolContext("1"));

        assertEquals("DETAIL", result);
        verify(orderGateway).getOrderDetail("1", "102");
    }

    @Test
    void updateDeliveryAddressResolvesOrderNumberBeforeCallingGateway() {
        OrderGateway orderGateway = mock(OrderGateway.class);
        stubRecentOrders(orderGateway);
        when(orderGateway.updateDeliveryAddress("1", "102", "No. 1 Road")).thenReturn("OK");
        OrderTools tools = new OrderTools(orderGateway, objectMapper, searchFormatter);

        String result = tools.updateDeliveryAddress("B20240502", "No. 1 Road", toolContext("1"));

        assertEquals("OK", result);
        verify(orderGateway).updateDeliveryAddress("1", "102", "No. 1 Road");
    }

    @Test
    void reorderResolvesOrderNumberBeforeCallingGateway() {
        OrderGateway orderGateway = mock(OrderGateway.class);
        stubRecentOrders(orderGateway);
        when(orderGateway.reorder("1", "102")).thenReturn("OK");
        OrderTools tools = new OrderTools(orderGateway, objectMapper, searchFormatter);

        String result = tools.reorder("B20240502", toolContext("1"));

        assertEquals("OK", result);
        verify(orderGateway).reorder("1", "102");
    }

    private ToolContext toolContext(String userId) {
        ToolContext context = mock(ToolContext.class);
        when(context.getContext()).thenReturn(Map.of("userId", userId));
        return context;
    }

    private void stubRecentOrders(OrderGateway orderGateway) {
        when(orderGateway.listRecentOrders("1", 10)).thenReturn("""
                {"records":[
                  {"id":101,"number":"A20240501"},
                  {"id":102,"number":"B20240502"}
                ]}
                """);
    }
}
