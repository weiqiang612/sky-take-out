package com.weiqiang.skyai.memory.advisor;

import com.weiqiang.skyai.advisor.UserContextAdvisor;
import com.weiqiang.skyai.advisor.UserProfileInjectionMetrics;
import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.memory.config.UserProfileMemoryProperties;
import com.weiqiang.skyai.memory.model.MemoryFactKey;
import com.weiqiang.skyai.memory.model.MemoryFactSourceType;
import com.weiqiang.skyai.memory.model.UserMemoryFact;
import com.weiqiang.skyai.memory.service.UserMemoryFactService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserContextAdvisorTests {

    @Test
    void allowedToolsIncludesSearchToolsForOrderIntents() {
        UserContextAdvisor advisor = new UserContextAdvisor(mock(UserMemoryFactService.class), new UserProfileMemoryProperties(), mock(UserProfileInjectionMetrics.class));
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
        UserContextAdvisor advisor = new UserContextAdvisor(mock(UserMemoryFactService.class), new UserProfileMemoryProperties(), mock(UserProfileInjectionMetrics.class));
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
        UserContextAdvisor advisor = new UserContextAdvisor(mock(UserMemoryFactService.class), new UserProfileMemoryProperties(), mock(UserProfileInjectionMetrics.class));
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
        UserContextAdvisor advisor = new UserContextAdvisor(mock(UserMemoryFactService.class), new UserProfileMemoryProperties(), mock(UserProfileInjectionMetrics.class));
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

    @Test
    void faqContextUsesStructuredDietaryMemoryBlock() {
        UserMemoryFactService userMemoryFactService = mock(UserMemoryFactService.class);
        UserMemoryFact dish = new UserMemoryFact();
        dish.setFactKey(MemoryFactKey.FAVORITE_DISHES.value());
        dish.setFactValue("平菇豆腐汤");
        dish.setSourceType(MemoryFactSourceType.USER_MANUAL);
        dish.setUpdatedAt(Instant.parse("2026-05-10T00:00:00Z"));
        when(userMemoryFactService.findFactsSorted("1")).thenReturn(List.of(dish));
        UserContextAdvisor advisor = new UserContextAdvisor(userMemoryFactService, new UserProfileMemoryProperties(), mock(UserProfileInjectionMetrics.class));
        IntentRecognitionResult intentResult = new IntentRecognitionResult(
                IntentType.FAQ,
                ConfidenceLevel.HIGH,
                Map.of(),
                List.of(),
                null,
                false,
                null
        );

        String contextBlock = (String) ReflectionTestUtils.invokeMethod(advisor, "buildContextBlock", intentResult, "1");

        assertTrue(contextBlock.contains("Relevant memory"));
        assertTrue(contextBlock.contains("平菇豆腐汤"));
    }

    @Test
    void adviseStreamPrependsContextBlockToPrompt() {
        UserMemoryFactService userMemoryFactService = mock(UserMemoryFactService.class);
        when(userMemoryFactService.operationalNotesSummary("1")).thenReturn("No known issues.");
        UserContextAdvisor advisor = new UserContextAdvisor(userMemoryFactService, new UserProfileMemoryProperties(), mock(UserProfileInjectionMetrics.class));
        IntentRecognitionResult intentResult = new IntentRecognitionResult(
                IntentType.ORDER_STATUS,
                ConfidenceLevel.HIGH,
                Map.of("order_id", "A123"),
                List.of(),
                null,
                false,
                null
        );
        AtomicReference<ChatClientRequest> capturedRequest = new AtomicReference<>();
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(org.mockito.ArgumentMatchers.any(ChatClientRequest.class))).thenAnswer(invocation -> {
            capturedRequest.set(invocation.getArgument(0));
            return Flux.just(mock(ChatClientResponse.class));
        });

        ChatClientRequest request = new ChatClientRequest(
                new Prompt("where is my order", new DefaultToolCallingChatOptions()),
                new java.util.HashMap<>(Map.of("userId", "1", "intentResult", intentResult))
        );

        advisor.adviseStream(request, chain).blockLast();

        SystemMessage systemMessage = (SystemMessage) capturedRequest.get().prompt().getInstructions().get(0);
        assertTrue(systemMessage.getText().contains("Order id: A123"));
        assertSame(intentResult, capturedRequest.get().context().get("intentResult"));
    }
}
