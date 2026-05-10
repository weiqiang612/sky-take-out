package com.weiqiang.skyai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.tools.gateway.MenuGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MenuTools {

    private final MenuGateway menuGateway;
    private final ObjectMapper objectMapper;
    private final ToolSearchFormatter searchFormatter;

    @Tool(description = "List menu categories, optionally filtered by dish or setmeal type.")
    public String listCategories(@ToolParam(description = "Category type: 1 for dishes, 2 for setmeals", required = false) Integer type) {
        return menuGateway.listCategories(type);
    }

    @Tool(description = "Search dish candidates by dish name, description, category name, or flavor before selecting a dish id.")
    public String searchDishes(@ToolParam(description = "Dish keyword or a fragment from the dish details") String keyword,
                               @ToolParam(description = "Maximum number of candidates to return", required = false) Integer limit) {
        return searchMenuItems("dishes", 1, keyword, limit, true);
    }

    @Tool(description = "Search setmeal candidates by name, description, category name, or included dishes before selecting a setmeal id.")
    public String searchSetmeals(@ToolParam(description = "Setmeal keyword or a fragment from the setmeal details") String keyword,
                                 @ToolParam(description = "Maximum number of candidates to return", required = false) Integer limit) {
        return searchMenuItems("setmeals", 2, keyword, limit, false);
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

    private String searchMenuItems(String source, Integer type, String keyword, Integer limit, boolean dishMode) {
        int max = limit == null || limit < 1 ? 10 : Math.min(limit, 20);
        List<ToolSearchFormatter.SearchCandidate> candidates = new ArrayList<>();
        JsonNode categories = readTree(menuGateway.listCategories(type));
        String normalizedKeyword = normalize(keyword);
        if (categories.isArray()) {
            categories.forEach(category -> {
                Long categoryId = longValue(category, "id");
                if (categoryId == null) {
                    return;
                }
                JsonNode items = readTree(dishMode ? menuGateway.listDishesByCategory(categoryId)
                        : menuGateway.listSetmealsByCategory(categoryId));
                if (items.isArray()) {
                    items.forEach(item -> addMenuCandidate(candidates, item, category, normalizedKeyword, keyword, dishMode));
                }
            });
        }
        List<ToolSearchFormatter.SearchCandidate> topCandidates = new ArrayList<>(candidates);
        topCandidates.sort(Comparator.comparingDouble(ToolSearchFormatter.SearchCandidate::confidence).reversed());
        if (topCandidates.size() > max) {
            topCandidates = new ArrayList<>(topCandidates.subList(0, max));
        }
        return searchFormatter.format(source, keyword, topCandidates);
    }

    private void addMenuCandidate(List<ToolSearchFormatter.SearchCandidate> candidates, JsonNode item, JsonNode category,
                                  String normalizedKeyword, String originalKeyword, boolean dishMode) {
        Long id = longValue(item, "id");
        if (id == null) {
            return;
        }
        String name = text(item, "name");
        String description = text(item, "description");
        String categoryName = text(item, "categoryName");
        String flavors = flavorsText(item.path("flavors"));
        String summary = buildSummary(item, category, dishMode);
        if (!hasText(normalizedKeyword)) {
            candidates.add(searchFormatter.candidate(id, name, summary, "recent", 0.5d));
            return;
        }
        String matchBy = matchMenu(normalizedKeyword, name, description, categoryName, flavors);
        if (matchBy != null) {
            candidates.add(searchFormatter.candidate(id, name, summary, matchBy, confidence(matchBy)));
        }
    }

    private String matchMenu(String keyword, String name, String description, String categoryName, String flavors) {
        if (contains(name, keyword)) {
            return "name";
        }
        if (contains(description, keyword)) {
            return "description";
        }
        if (contains(categoryName, keyword)) {
            return "category";
        }
        if (contains(flavors, keyword)) {
            return "flavor";
        }
        return null;
    }

    private double confidence(String matchBy) {
        return switch (matchBy) {
            case "name" -> 1.0d;
            case "flavor" -> 0.88d;
            case "category", "description" -> 0.82d;
            default -> 0.5d;
        };
    }

    private String buildSummary(JsonNode item, JsonNode category, boolean dishMode) {
        String categoryName = text(category, "name");
        String status = text(item, "status");
        String price = text(item, "price");
        String description = text(item, "description");
        return "category=" + defaultText(categoryName)
                + ", status=" + defaultText(status)
                + ", price=" + defaultText(price)
                + ", description=" + defaultText(description)
                + (dishMode ? ", flavors=" + flavorsText(item.path("flavors")) : "");
    }

    private String flavorsText(JsonNode flavorsNode) {
        if (!flavorsNode.isArray()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        flavorsNode.forEach(f -> {
            String name = text(f, "name");
            String value = text(f, "value");
            String text = join(name, value);
            if (hasText(text)) {
                values.add(text);
            }
        });
        return String.join("; ", values);
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

    private String join(String first, String second) {
        if (!hasText(first)) {
            return second;
        }
        if (!hasText(second)) {
            return first;
        }
        return first + "=" + second;
    }
}
