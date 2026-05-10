package com.weiqiang.skyai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.tools.gateway.AddressGateway;
import com.weiqiang.skyai.tools.gateway.CartGateway;
import com.weiqiang.skyai.tools.gateway.MenuGateway;
import com.weiqiang.skyai.tools.gateway.OrderGateway;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchToolsTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void searchOrdersReturnsMatchingOrderCandidate() throws Exception {
        OrderGateway orderGateway = mock(OrderGateway.class);
        when(orderGateway.listRecentOrders("1", 10)).thenReturn("""
                {"total":2,"records":[
                  {"id":101,"number":"A20240501","orderDishes":"宫保鸡丁*1;","consignee":"张三","address":"上海市浦东新区","phone":"13800138000","remark":"少辣"},
                  {"id":102,"number":"B20240502","orderDishes":"鱼香肉丝*1;","consignee":"李四","address":"北京市朝阳区","phone":"13900139000","remark":"正常"}
                ]}
                """);
        OrderTools tools = new OrderTools(orderGateway, objectMapper, new ToolSearchFormatter(objectMapper));

        JsonNode result = objectMapper.readTree(tools.searchOrders("A20240501", 10, toolContext("1")));

        assertEquals("orders", result.get("source").asText());
        assertTrue(result.get("matched").asBoolean());
        assertFalse(result.get("needConfirm").asBoolean());
        assertEquals(1, result.get("candidateCount").asInt());
        assertEquals(101, result.get("candidates").get(0).get("id").asInt());
    }

    @Test
    void searchDishesReturnsMatchingDishCandidate() throws Exception {
        MenuGateway menuGateway = mock(MenuGateway.class);
        when(menuGateway.listCategories(1)).thenReturn("""
                [{"id":1,"name":"热菜"},{"id":2,"name":"凉菜"}]
                """);
        when(menuGateway.listDishesByCategory(1L)).thenReturn("""
                [{"id":11,"name":"宫保鸡丁","description":"经典川菜","status":1,"categoryName":"热菜","price":32.5,"flavors":[{"name":"辣度","value":"微辣"}]}]
                """);
        when(menuGateway.listDishesByCategory(2L)).thenReturn("[]");
        MenuTools tools = new MenuTools(menuGateway, objectMapper, new ToolSearchFormatter(objectMapper));

        JsonNode result = objectMapper.readTree(tools.searchDishes("宫保鸡丁", 10));

        assertEquals("dishes", result.get("source").asText());
        assertTrue(result.get("matched").asBoolean());
        assertEquals(1, result.get("candidateCount").asInt());
        assertEquals(11, result.get("candidates").get(0).get("id").asInt());
    }

    @Test
    void searchSetmealsReturnsMatchingSetmealCandidate() throws Exception {
        MenuGateway menuGateway = mock(MenuGateway.class);
        when(menuGateway.listCategories(2)).thenReturn("""
                [{"id":21,"name":"套餐"}]
                """);
        when(menuGateway.listSetmealsByCategory(21L)).thenReturn("""
                [{"id":201,"name":"商务套餐","description":"双人套餐","status":1,"categoryName":"套餐","price":58.0}]
                """);
        MenuTools tools = new MenuTools(menuGateway, objectMapper, new ToolSearchFormatter(objectMapper));

        JsonNode result = objectMapper.readTree(tools.searchSetmeals("商务套餐", 10));

        assertEquals("setmeals", result.get("source").asText());
        assertTrue(result.get("matched").asBoolean());
        assertEquals(1, result.get("candidateCount").asInt());
        assertEquals(201, result.get("candidates").get(0).get("id").asInt());
    }

    @Test
    void searchCartItemsReturnsMatchingCartCandidate() throws Exception {
        CartGateway cartGateway = mock(CartGateway.class);
        when(cartGateway.listCart("1")).thenReturn("""
                [
                  {"id":9001,"name":"宫保鸡丁","userId":1,"dishId":11,"setmealId":null,"dishFlavor":"微辣","number":2,"amount":32.5},
                  {"id":9002,"name":"商务套餐","userId":1,"dishId":null,"setmealId":201,"dishFlavor":null,"number":1,"amount":58.0}
                ]
                """);
        CartTools tools = new CartTools(cartGateway, objectMapper, new ToolSearchFormatter(objectMapper));

        JsonNode result = objectMapper.readTree(tools.searchCartItems("微辣", toolContext("1")));

        assertEquals("cart", result.get("source").asText());
        assertTrue(result.get("matched").asBoolean());
        assertEquals(1, result.get("candidateCount").asInt());
        assertEquals(11, result.get("candidates").get(0).get("id").asInt());
    }

    @Test
    void searchAddressesReturnsMatchingAddressCandidate() throws Exception {
        AddressGateway addressGateway = mock(AddressGateway.class);
        when(addressGateway.listAddresses("1")).thenReturn("""
                [
                  {"id":301,"consignee":"张三","phone":"13800138000","label":"家","detail":"上海市浦东新区XX路1号","isDefault":1},
                  {"id":302,"consignee":"李四","phone":"13900139000","label":"公司","detail":"北京市朝阳区XX大厦","isDefault":0}
                ]
                """);
        AddressTools tools = new AddressTools(addressGateway, objectMapper, new ToolSearchFormatter(objectMapper));

        JsonNode result = objectMapper.readTree(tools.searchAddresses("公司", toolContext("1")));

        assertEquals("addresses", result.get("source").asText());
        assertTrue(result.get("matched").asBoolean());
        assertEquals(1, result.get("candidateCount").asInt());
        assertEquals(302, result.get("candidates").get(0).get("id").asInt());
    }

    private ToolContext toolContext(String userId) {
        ToolContext context = mock(ToolContext.class);
        when(context.getContext()).thenReturn(Map.of("userId", userId));
        return context;
    }
}
