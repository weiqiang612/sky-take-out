package com.weiqiang.skyai.memory.advisor;

import com.weiqiang.skyai.advisor.IntentRecognitionAdvisor;
import com.weiqiang.skyai.advisor.UserProfileInjectionMetrics;
import com.weiqiang.skyai.intent_recognition.client.CustomerIntentRecognitionClient;
import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionRequest;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.memory.config.UserProfileMemoryProperties;
import com.weiqiang.skyai.memory.service.ChatHistoryService;
import com.weiqiang.skyai.memory.service.UserMemoryFactService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IntentRecognitionAdvisorTests {

    @Test
    void adviseCallPrependsProfileSummaryToRecognitionInput() {
        CustomerIntentRecognitionClient client = mock(CustomerIntentRecognitionClient.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        UserMemoryFactService userMemoryFactService = mock(UserMemoryFactService.class);
        UserProfileMemoryProperties properties = new UserProfileMemoryProperties();
        UserProfileInjectionMetrics metrics = mock(UserProfileInjectionMetrics.class);

        IntentRecognitionResult intentResult = new IntentRecognitionResult(
                IntentType.CANCEL_ORDER,
                ConfidenceLevel.HIGH,
                Map.of("order_id", "A123"),
                List.of(IntentType.CANCEL_ORDER),
                null,
                false,
                null
        );
        when(client.recognize(any(IntentRecognitionRequest.class))).thenReturn(intentResult);
        when(chatHistoryService.buildHistory("conv-1", "user-1")).thenReturn(List.of());
        when(userMemoryFactService.userProfileNotesSummary("user-1")).thenReturn("我在减脂");

        IntentRecognitionAdvisor advisor = new IntentRecognitionAdvisor(client, chatHistoryService, userMemoryFactService, properties, metrics);
        AtomicReference<ChatClientRequest> capturedRequest = new AtomicReference<>();
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any(ChatClientRequest.class))).thenAnswer(invocation -> {
            capturedRequest.set(invocation.getArgument(0));
            return mock(ChatClientResponse.class);
        });

        ChatClientRequest request = new ChatClientRequest(new Prompt("cancel my order"), new HashMap<>(Map.of(
                "conversationId", "conv-1",
                "userId", "user-1"
        )));

        advisor.adviseCall(request, chain);

        IntentRecognitionRequest recognitionRequest = verifyClientRequest(client);
        assertTrue(recognitionRequest.message().contains("User profile notes: 我在减脂"));
        assertTrue(recognitionRequest.message().contains("cancel my order"));
        assertEquals(intentResult, capturedRequest.get().context().get("intentResult"));
    }

    @Test
    void adviseCallSkipsProfileSummaryWhenDisabled() {
        CustomerIntentRecognitionClient client = mock(CustomerIntentRecognitionClient.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        UserMemoryFactService userMemoryFactService = mock(UserMemoryFactService.class);
        UserProfileMemoryProperties properties = new UserProfileMemoryProperties();
        properties.setIntentRecognitionSummaryEnabled(false);
        UserProfileInjectionMetrics metrics = mock(UserProfileInjectionMetrics.class);

        IntentRecognitionResult intentResult = new IntentRecognitionResult(
                IntentType.CANCEL_ORDER,
                ConfidenceLevel.HIGH,
                Map.of("order_id", "A123"),
                List.of(IntentType.CANCEL_ORDER),
                null,
                false,
                null
        );
        when(client.recognize(any(IntentRecognitionRequest.class))).thenReturn(intentResult);
        when(chatHistoryService.buildHistory("conv-1", "user-1")).thenReturn(List.of());

        IntentRecognitionAdvisor advisor = new IntentRecognitionAdvisor(client, chatHistoryService, userMemoryFactService, properties, metrics);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(mock(ChatClientResponse.class));

        ChatClientRequest request = new ChatClientRequest(new Prompt("cancel my order"), new HashMap<>(Map.of(
                "conversationId", "conv-1",
                "userId", "user-1"
        )));

        advisor.adviseCall(request, chain);

        IntentRecognitionRequest recognitionRequest = verifyClientRequest(client);
        assertEquals("cancel my order", recognitionRequest.message());
        verifyNoInteractions(userMemoryFactService);
    }

    private IntentRecognitionRequest verifyClientRequest(CustomerIntentRecognitionClient client) {
        var captor = org.mockito.ArgumentCaptor.forClass(IntentRecognitionRequest.class);
        verify(client).recognize(captor.capture());
        return captor.getValue();
    }
}
