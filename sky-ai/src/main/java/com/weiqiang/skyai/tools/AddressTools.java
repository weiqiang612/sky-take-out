package com.weiqiang.skyai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.tools.gateway.AddressGateway;
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
public class AddressTools {

    private final AddressGateway addressGateway;
    private final ObjectMapper objectMapper;
    private final ToolSearchFormatter searchFormatter;

    @Tool(description = "List all saved delivery addresses for the current user.")
    public String listAddresses(ToolContext context) {
        return addressGateway.listAddresses(ToolUser.userId(context));
    }

    @Tool(description = "Search the current user's saved addresses by consignee, phone, label, or detail before updating or selecting an address id.")
    public String searchAddresses(@ToolParam(description = "Address keyword or a fragment from the address details") String keyword,
                                  ToolContext context) {
        JsonNode addresses = readTree(addressGateway.listAddresses(ToolUser.userId(context)));
        List<ToolSearchFormatter.SearchCandidate> candidates = new ArrayList<>();
        if (addresses.isArray()) {
            String normalizedKeyword = normalize(keyword);
            addresses.forEach(address -> addAddressCandidate(candidates, address, normalizedKeyword, keyword));
        }
        candidates.sort(Comparator.comparingDouble(ToolSearchFormatter.SearchCandidate::confidence).reversed());
        return searchFormatter.format("addresses", keyword, candidates);
    }

    @Tool(description = "Get the current user's default delivery address.")
    public String getDefaultAddress(ToolContext context) {
        return addressGateway.getDefaultAddress(ToolUser.userId(context));
    }

    @Tool(description = "Set one saved address as the current user's default address.")
    public String setDefaultAddress(@ToolParam(description = "Address id") Long addressId, ToolContext context) {
        return addressGateway.setDefaultAddress(ToolUser.userId(context), addressId);
    }

    @Tool(description = "Update a saved delivery address.")
    public String updateAddress(@ToolParam(description = "Address id") Long addressId,
                                @ToolParam(description = "Consignee name") String consignee,
                                @ToolParam(description = "Phone number") String phone,
                                @ToolParam(description = "Detailed delivery address") String detail,
                                ToolContext context) {
        return addressGateway.updateAddress(ToolUser.userId(context), addressId, consignee, phone, detail);
    }

    private void addAddressCandidate(List<ToolSearchFormatter.SearchCandidate> candidates, JsonNode address,
                                     String normalizedKeyword, String originalKeyword) {
        Long id = longValue(address, "id");
        if (id == null) {
            return;
        }
        String consignee = text(address, "consignee");
        String phone = text(address, "phone");
        String label = text(address, "label");
        String detail = text(address, "detail");
        String summary = "consignee=" + defaultText(consignee)
                + ", phone=" + defaultText(phone)
                + ", label=" + defaultText(label)
                + ", detail=" + defaultText(detail)
                + ", default=" + defaultText(text(address, "isDefault"));
        if (!hasText(normalizedKeyword)) {
            candidates.add(searchFormatter.candidate(id, consignee, summary, "recent", 0.5d));
            return;
        }
        String matchBy = matchAddress(normalizedKeyword, consignee, phone, label, detail);
        if (matchBy != null) {
            candidates.add(searchFormatter.candidate(id, consignee, summary, matchBy, confidence(matchBy)));
        }
    }

    private String matchAddress(String keyword, String consignee, String phone, String label, String detail) {
        if (contains(consignee, keyword)) {
            return "consignee";
        }
        if (contains(phone, keyword)) {
            return "phone";
        }
        if (contains(label, keyword)) {
            return "label";
        }
        if (contains(detail, keyword)) {
            return "detail";
        }
        return null;
    }

    private double confidence(String matchBy) {
        return switch (matchBy) {
            case "consignee" -> 1.0d;
            case "phone" -> 0.98d;
            case "label" -> 0.9d;
            case "detail" -> 0.85d;
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
