package com.weiqiang.skyai.tools;

import com.weiqiang.skyai.tools.gateway.OrderGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class OrderTools {

    private final OrderGateway orderGateway;
    private final ObjectMapper objectMapper;
    private final ToolSearchFormatter searchFormatter;

    @Tool(description = "Get the current user's order details by order id.")
    public String getOrderDetail(@ToolParam(description = "Order id") String orderId, ToolContext context) {
        return orderGateway.getOrderDetail(ToolUser.userId(context), orderId);
    }

    @Tool(description = "List the current user's most recent orders.")
    public String listRecentOrders(@ToolParam(description = "Maximum number of orders to return") int pageSize, ToolContext context) {
        return orderGateway.listRecentOrders(ToolUser.userId(context), pageSize);
    }

    @Tool(description = "Search the current user's orders by order number, dish name, consignee, phone, address, or remark before selecting an order id.")
    public String searchOrders(@ToolParam(description = "Order keyword or a fragment from the order details") String keyword,
                               @ToolParam(description = "Maximum number of candidates to return", required = false) Integer pageSize,
                               ToolContext context) {
        String userId = ToolUser.userId(context);
        int limit = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 20);
        String payload = orderGateway.listRecentOrders(userId, limit);
        List<ToolSearchFormatter.SearchCandidate> candidates = new ArrayList<>();
        JsonNode records = readTree(payload).path("records");
        if (records.isArray()) {
            String normalizedKeyword = normalize(keyword);
            records.forEach(order -> addOrderCandidate(candidates, order, normalizedKeyword, keyword));
        }
        List<ToolSearchFormatter.SearchCandidate> topCandidates = new ArrayList<>(candidates);
        topCandidates.sort(Comparator.comparingDouble(ToolSearchFormatter.SearchCandidate::confidence).reversed());
        if (topCandidates.size() > limit) {
            topCandidates = new ArrayList<>(topCandidates.subList(0, limit));
        }
        return searchFormatter.format("orders", keyword, topCandidates);
    }

    @Tool(description = "Cancel an unpaid or unconfirmed order for the current user.")
    public String cancelOrder(@ToolParam(description = "Order id") String orderId, ToolContext context) {
        return orderGateway.cancelOrder(ToolUser.userId(context), orderId);
    }

    @Tool(description = "Request a refund for an order.")
    public String requestRefund(@ToolParam(description = "Order id") String orderId,
                                @ToolParam(description = "Refund reason") String reason,
                                ToolContext context) {
        return orderGateway.requestRefund(ToolUser.userId(context), orderId, reason);
    }

    @Tool(description = "Update the delivery address for an order.")
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

    private void addOrderCandidate(List<ToolSearchFormatter.SearchCandidate> candidates, JsonNode order,
                                   String normalizedKeyword, String originalKeyword) {
        Long id = order.path("id").isMissingNode() ? null : order.path("id").asLong();
        if (id == null) {
            return;
        }
        String number = text(order, "number");
        String orderDishes = text(order, "orderDishes");
        String phone = text(order, "phone");
        String address = text(order, "address");
        String consignee = text(order, "consignee");
        String remark = text(order, "remark");
        String summary = buildSummary(number, orderDishes, consignee, address, phone);
        if (!hasText(normalizedKeyword)) {
            candidates.add(searchFormatter.candidate(id, "Order " + number, summary, "recent", 0.5d));
            return;
        }
        String matchBy = matchOrder(normalizedKeyword, number, orderDishes, phone, address, consignee, remark);
        if (matchBy != null) {
            candidates.add(searchFormatter.candidate(id, "Order " + number, summary, matchBy, confidence(matchBy)));
        } else if (matchesDigits(normalizedKeyword, order)) {
            candidates.add(searchFormatter.candidate(id, "Order " + number, summary, "recent", 0.4d));
        }
    }

    private String matchOrder(String keyword, String number, String orderDishes, String phone, String address,
                              String consignee, String remark) {
        if (contains(number, keyword)) {
            return "number";
        }
        if (contains(phone, keyword)) {
            return "phone";
        }
        if (contains(orderDishes, keyword)) {
            return "dishes";
        }
        if (contains(consignee, keyword)) {
                return "consignee";
            }
        if (contains(address, keyword)) {
            return "address";
        }
        if (contains(remark, keyword)) {
            return "remark";
        }
        return null;
    }

    private boolean matchesDigits(String keyword, JsonNode order) {
        return keyword.matches("\\d+") && contains(text(order, "number"), keyword);
    }

    private double confidence(String matchBy) {
        return switch (matchBy) {
            case "number" -> 1.0d;
            case "phone" -> 0.98d;
            case "dishes" -> 0.9d;
            case "consignee", "address", "remark" -> 0.85d;
            default -> 0.5d;
        };
    }

    private String buildSummary(String number, String orderDishes, String consignee, String address, String phone) {
        return "number=" + defaultText(number)
                + ", dishes=" + defaultText(orderDishes)
                + ", consignee=" + defaultText(consignee)
                + ", address=" + defaultText(address)
                + ", phone=" + defaultText(phone);
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private boolean contains(String source, String keyword) {
        return hasText(source) && hasText(keyword) && source.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String defaultText(String value) {
        return hasText(value) ? value : "unknown";
    }
}
