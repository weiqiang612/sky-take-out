package com.weiqiang.skyai.memory.advisor;

import com.weiqiang.skyai.advisor.ToolPolicyRegistry;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolPolicyRegistryTests {

    @Test
    void mergeAllowedToolsAlwaysIncludesCurrentUserReadTools() {
        Set<String> merged = ToolPolicyRegistry.mergeAllowedTools(Set.of());

        assertTrue(merged.contains("searchOrders"));
        assertTrue(merged.contains("searchDishes"));
        assertTrue(merged.contains("searchSetmeals"));
        assertTrue(merged.contains("searchCartItems"));
        assertTrue(merged.contains("searchAddresses"));
        assertTrue(merged.contains("getShopStatus"));
        assertTrue(merged.contains("cleanCart"));
        assertTrue(merged.contains("cancelOrder"));
    }

    @Test
    void mergeAllowedToolsPreservesIntentSpecificWriteTools() {
        Set<String> merged = ToolPolicyRegistry.mergeAllowedTools(Set.of("cancelOrder", "cleanCart"));

        assertTrue(merged.contains("cancelOrder"));
        assertTrue(merged.contains("cleanCart"));
        assertTrue(merged.contains("searchOrders"));
    }
}
