package com.weiqiang.skyai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.memory.advisor.DynamicToolCallbackRegistry;
import com.weiqiang.skyai.tools.gateway.AddressGateway;
import com.weiqiang.skyai.tools.gateway.CartGateway;
import com.weiqiang.skyai.tools.gateway.MenuGateway;
import com.weiqiang.skyai.tools.gateway.OrderGateway;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DynamicToolCallbackRegistryTest {

    @Test
    void registryExposesAllAnnotatedToolsAndSelectsOnlyRequestedCallbacks() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolSearchFormatter searchFormatter = new ToolSearchFormatter(objectMapper);
        OrderTools orderTools = new OrderTools(mock(OrderGateway.class), objectMapper, searchFormatter);
        MenuTools menuTools = new MenuTools(mock(MenuGateway.class), objectMapper, searchFormatter);
        CartTools cartTools = new CartTools(mock(CartGateway.class), objectMapper, searchFormatter);
        AddressTools addressTools = new AddressTools(mock(AddressGateway.class), objectMapper, searchFormatter);

        DynamicToolCallbackRegistry registry = new DynamicToolCallbackRegistry(orderTools, menuTools, cartTools, addressTools);

        assertEquals(26, registry.size());
        assertTrue(registry.availableToolNames().contains("searchOrders"));
        assertTrue(registry.availableToolNames().contains("cleanCart"));
        assertTrue(registry.availableToolNames().contains("updateAddress"));

        Set<String> allowed = new LinkedHashSet<>(List.of(
                "searchDishes",
                "searchSetmeals",
                "listCategories",
                "listDishesByCategory",
                "listSetmealsByCategory",
                "listSetmealDishes",
                "getShopStatus"
        ));

        List<ToolCallback> selected = registry.selectCallbacks(allowed);
        assertEquals(7, selected.size());
        assertEquals(allowed, selected.stream()
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }
}

