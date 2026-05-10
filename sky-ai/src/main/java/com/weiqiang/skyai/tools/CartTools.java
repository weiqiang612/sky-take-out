package com.weiqiang.skyai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.tools.gateway.CartGateway;
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
public class CartTools {

    private final CartGateway cartGateway;
    private final ObjectMapper objectMapper;
    private final ToolSearchFormatter searchFormatter;

    @Tool(description = "List all items in the current user's shopping cart.")
    public String listCart(ToolContext context) {
        return cartGateway.listCart(ToolUser.userId(context));
    }

    @Tool(description = "Search the current user's cart items by item name or flavor before removing one item.")
    public String searchCartItems(@ToolParam(description = "Cart item keyword or flavor fragment") String keyword,
                                  ToolContext context) {
        String userId = ToolUser.userId(context);
        JsonNode cartItems = readTree(cartGateway.listCart(userId));
        List<ToolSearchFormatter.SearchCandidate> candidates = new ArrayList<>();
        if (cartItems.isArray()) {
            String normalizedKeyword = normalize(keyword);
            cartItems.forEach(item -> addCartCandidate(candidates, item, normalizedKeyword, keyword));
        }
        candidates.sort(Comparator.comparingDouble(ToolSearchFormatter.SearchCandidate::confidence).reversed());
        return searchFormatter.format("cart", keyword, candidates);
    }

    @Tool(description = "Add one dish to the current user's shopping cart. If the user only provides a dish name, search the menu first and then add the unique match directly.")
    public String addDishToCart(@ToolParam(description = "Dish id") Long dishId,
                                @ToolParam(description = "Dish flavor", required = false) String dishFlavor,
                                ToolContext context) {
        return cartGateway.addDishToCart(ToolUser.userId(context), dishId, dishFlavor);
    }

    @Tool(description = "Add one setmeal to the current user's shopping cart. If the user only provides a setmeal name, search the menu first and then add the unique match directly.")
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

    @Tool(description = "Clear all items from the current user's shopping cart.")
    public String cleanCart(ToolContext context) {
        return cartGateway.cleanCart(ToolUser.userId(context));
    }

    private void addCartCandidate(List<ToolSearchFormatter.SearchCandidate> candidates, JsonNode item,
                                  String normalizedKeyword, String originalKeyword) {
        Long id = longValue(item, "dishId");
        String kind = "dish";
        if (id == null) {
            id = longValue(item, "setmealId");
            kind = "setmeal";
        }
        if (id == null) {
            return;
        }
        String name = text(item, "name");
        String flavor = text(item, "dishFlavor");
        String summary = "kind=" + kind
                + ", flavor=" + defaultText(flavor)
                + ", quantity=" + defaultText(text(item, "number"))
                + ", amount=" + defaultText(text(item, "amount"));
        if (!hasText(normalizedKeyword)) {
            candidates.add(searchFormatter.candidate(id, name, summary, "recent", 0.5d));
            return;
        }
        String matchBy = matchCart(normalizedKeyword, name, flavor, kind);
        if (matchBy != null) {
            candidates.add(searchFormatter.candidate(id, name, summary, matchBy, confidence(matchBy)));
        }
    }

    private String matchCart(String keyword, String name, String flavor, String kind) {
        if (contains(name, keyword)) {
            return "name";
        }
        if (contains(flavor, keyword)) {
            return "flavor";
        }
        if (contains(kind, keyword)) {
            return "kind";
        }
        return null;
    }

    private double confidence(String matchBy) {
        return switch (matchBy) {
            case "name" -> 1.0d;
            case "flavor" -> 0.88d;
            case "kind" -> 0.7d;
            default -> 0.5d;
        };
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "[]" : json);
        } catch (Exception ex) {
            return objectMapper.createArrayNode();
        }
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asLong();
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
