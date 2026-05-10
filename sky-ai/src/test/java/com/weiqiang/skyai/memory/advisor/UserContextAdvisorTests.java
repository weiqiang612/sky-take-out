package com.weiqiang.skyai.memory.advisor;

import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.memory.repository.UserMemoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class UserContextAdvisorTests {

    @Test
    void allowedToolsIncludesSearchToolsForOrderIntents() {
        UserContextAdvisor advisor = new UserContextAdvisor(mock(UserMemoryRepository.class));
        IntentRecognitionResult intentResult = new IntentRecognitionResult(
                IntentType.ORDER_STATUS,
                ConfidenceLevel.HIGH,
                Map.of(),
                List.of(),
                null,
                false,
                null
        );

        @SuppressWarnings("unchecked")
        Set<String> allowedTools = (Set<String>) ReflectionTestUtils.invokeMethod(advisor, "allowedTools", intentResult);

        assertTrue(allowedTools.contains("searchOrders"));
        assertTrue(allowedTools.contains("getOrderDetail"));
        assertTrue(allowedTools.contains("remindOrder"));
    }

    @Test
    void allowedToolsStayEmptyForFaqAndOther() {
        UserContextAdvisor advisor = new UserContextAdvisor(mock(UserMemoryRepository.class));
        IntentRecognitionResult intentResult = new IntentRecognitionResult(
                IntentType.OTHER,
                ConfidenceLevel.LOW,
                Map.of(),
                List.of(),
                null,
                false,
                null
        );

        @SuppressWarnings("unchecked")
        Set<String> allowedTools = (Set<String>) ReflectionTestUtils.invokeMethod(advisor, "allowedTools", intentResult);

        assertTrue(allowedTools.isEmpty());
    }

    @Test
    void allowedToolsIncludesMenuSearchToolsForCartManagement() {
        UserContextAdvisor advisor = new UserContextAdvisor(mock(UserMemoryRepository.class));
        IntentRecognitionResult intentResult = new IntentRecognitionResult(
                IntentType.CART_MANAGEMENT,
                ConfidenceLevel.HIGH,
                Map.of(),
                List.of(),
                null,
                false,
                null
        );

        @SuppressWarnings("unchecked")
        Set<String> allowedTools = (Set<String>) ReflectionTestUtils.invokeMethod(advisor, "allowedTools", intentResult);

        assertTrue(allowedTools.contains("searchDishes"));
        assertTrue(allowedTools.contains("searchSetmeals"));
        assertTrue(allowedTools.contains("searchCartItems"));
        assertTrue(allowedTools.contains("addDishToCart"));
        assertTrue(allowedTools.contains("addSetmealToCart"));
    }

    @Test
    void cartManagementContextEncouragesSearchThenDirectExecution() {
        UserContextAdvisor advisor = new UserContextAdvisor(mock(UserMemoryRepository.class));
        IntentRecognitionResult intentResult = new IntentRecognitionResult(
                IntentType.CART_MANAGEMENT,
                ConfidenceLevel.HIGH,
                Map.of(),
                List.of(),
                null,
                false,
                null
        );

        String contextBlock = (String) ReflectionTestUtils.invokeMethod(advisor, "buildContextBlock", intentResult, "1");

        String normalized = contextBlock.toLowerCase();

        assertTrue(normalized.contains("search the menu first"));
        assertTrue(normalized.contains("add the unique match directly"));
        assertTrue(normalized.contains("do not ask the user to provide an id"));
    }
}
