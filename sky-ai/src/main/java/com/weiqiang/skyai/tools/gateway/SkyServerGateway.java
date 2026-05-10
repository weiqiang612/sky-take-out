package com.weiqiang.skyai.tools.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SkyServerGateway implements OrderGateway, MenuGateway, CartGateway, AddressGateway {

    private final RestClient.Builder restClientBuilder;

    @Value("${skyai.sky-server.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    public String getOrderDetail(String userId, String orderId) {
        return get(userId, "/ai/customer/orders/" + orderId);
    }

    @Override
    public String listRecentOrders(String userId, int pageSize) {
        return get(userId, "/ai/customer/orders/recent?pageSize=" + pageSize);
    }

    @Override
    public String cancelOrder(String userId, String orderId) {
        return put(userId, "/ai/customer/orders/" + orderId + "/cancel", Map.of());
    }

    @Override
    public String requestRefund(String userId, String orderId, String reason) {
        return put(userId, "/ai/customer/orders/" + orderId + "/refund", Map.of("reason", reason));
    }

    @Override
    public String updateDeliveryAddress(String userId, String orderId, String newAddress) {
        return put(userId, "/ai/customer/orders/" + orderId + "/address", Map.of("newAddress", newAddress));
    }

    @Override
    public String remindOrder(String userId, String orderId) {
        return get(userId, "/ai/customer/orders/" + orderId + "/reminder");
    }

    @Override
    public String reorder(String userId, String orderId) {
        return post(userId, "/ai/customer/orders/" + orderId + "/reorder", Map.of());
    }

    @Override
    public String listCategories(Integer type) {
        return get(null, type == null ? "/ai/customer/categories" : "/ai/customer/categories?type=" + type);
    }

    @Override
    public String listDishesByCategory(Long categoryId) {
        return get(null, "/ai/customer/dishes?categoryId=" + categoryId);
    }

    @Override
    public String listSetmealsByCategory(Long categoryId) {
        return get(null, "/ai/customer/setmeals?categoryId=" + categoryId);
    }

    @Override
    public String listSetmealDishes(Long setmealId) {
        return get(null, "/ai/customer/setmeals/" + setmealId + "/dishes");
    }

    @Override
    public String getShopStatus() {
        return get(null, "/ai/customer/shop/status");
    }

    @Override
    public String listCart(String userId) {
        return get(userId, "/ai/customer/cart");
    }

    @Override
    public String addDishToCart(String userId, Long dishId, String dishFlavor) {
        return post(userId, "/ai/customer/cart/dish", Map.of("dishId", dishId, "dishFlavor", dishFlavor == null ? "" : dishFlavor));
    }

    @Override
    public String addSetmealToCart(String userId, Long setmealId) {
        return post(userId, "/ai/customer/cart/setmeal", Map.of("setmealId", setmealId));
    }

    @Override
    public String removeCartItem(String userId, Long dishId, Long setmealId, String dishFlavor) {
        Map<String, Object> body = new HashMap<>();
        if (dishId != null) {
            body.put("dishId", dishId);
        }
        if (setmealId != null) {
            body.put("setmealId", setmealId);
        }
        if (dishFlavor != null) {
            body.put("dishFlavor", dishFlavor);
        }
        return post(userId, "/ai/customer/cart/remove", body);
    }

    @Override
    public String cleanCart(String userId) {
        return delete(userId, "/ai/customer/cart");
    }

    @Override
    public String listAddresses(String userId) {
        return get(userId, "/ai/customer/addresses");
    }

    @Override
    public String getDefaultAddress(String userId) {
        return get(userId, "/ai/customer/addresses/default");
    }

    @Override
    public String setDefaultAddress(String userId, Long addressId) {
        return put(userId, "/ai/customer/addresses/default", Map.of("id", addressId));
    }

    @Override
    public String updateAddress(String userId, Long addressId, String consignee, String phone, String detail) {
        return put(userId, "/ai/customer/addresses/" + addressId, Map.of("consignee", consignee, "phone", phone, "detail", detail));
    }

    private String get(String userId, String uri) {
        return exchange(() -> client().get().uri(uri).headers(headers -> userHeader(headers, userId)).retrieve().body(JsonNode.class));
    }

    private String post(String userId, String uri, Map<String, Object> body) {
        return exchange(() -> client().post().uri(uri).headers(headers -> userHeader(headers, userId)).body(body).retrieve().body(JsonNode.class));
    }

    private String put(String userId, String uri, Map<String, Object> body) {
        return exchange(() -> client().put().uri(uri).headers(headers -> userHeader(headers, userId)).body(body).retrieve().body(JsonNode.class));
    }

    private String delete(String userId, String uri) {
        return exchange(() -> client().delete().uri(uri).headers(headers -> userHeader(headers, userId)).retrieve().body(JsonNode.class));
    }

    private String exchange(SkyServerCall call) {
        try {
            JsonNode result = call.execute();
            if (result == null || result.path("code").asInt() != 1) {
                return "FAIL: " + (result == null ? "empty response" : result.path("msg").asText("server rejected request"));
            }
            return result.path("data").isMissingNode() || result.path("data").isNull() ? "OK" : result.path("data").toString();
        } catch (Exception ex) {
            return "FAIL: " + ex.getMessage();
        }
    }

    private RestClient client() {
        return restClientBuilder.baseUrl(baseUrl).build();
    }

    private void userHeader(HttpHeaders headers, String userId) {
        if (userId != null && !userId.isBlank()) {
            headers.set("X-AI-User-Id", userId);
        }
    }

    private interface SkyServerCall {
        JsonNode execute();
    }
}
