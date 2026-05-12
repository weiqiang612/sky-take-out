package com.weiqiang.skyai.memory.advisor;

import com.weiqiang.skyai.advisor.IntentRecognitionAdvisor;
import com.weiqiang.skyai.intent_recognition.client.CustomerIntentRecognitionClient;
import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionRequest;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.memory.service.ChatHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntentRecognitionAdvisorTests {

    @Test
    void adviseStreamWritesIntentResultIntoContext() {
        CustomerIntentRecognitionClient client = mock(CustomerIntentRecognitionClient.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        IntentRecognitionResult intentResult = new IntentRecognitionResult(
                IntentType.CANCEL_ORDER,
                ConfidenceLevel.HIGH,
                Map.of("order_id", "A123"),
                List.of(IntentType.CANCEL_ORDER),
                null,
                false,
                null
        );
        when(client.recognize(org.mockito.ArgumentMatchers.any(IntentRecognitionRequest.class))).thenReturn(intentResult);
        when(chatHistoryService.buildHistory("conv-1", "user-1")).thenReturn(List.of());

        IntentRecognitionAdvisor advisor = new IntentRecognitionAdvisor(client, chatHistoryService);
        AtomicReference<ChatClientRequest> capturedRequest = new AtomicReference<>();
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(any(ChatClientRequest.class))).thenAnswer(invocation -> {
            capturedRequest.set(invocation.getArgument(0));
            return Flux.just(mock(ChatClientResponse.class));
        });

        ChatClientRequest request = new ChatClientRequest(new Prompt("cancel my order"), new HashMap<>(Map.of(
                "conversationId", "conv-1",
                "userId", "user-1"
        )));

        advisor.adviseStream(request, chain).blockLast();

        assertSame(intentResult, capturedRequest.get().context().get("intentResult"));
        assertEquals("A123", ((IntentRecognitionResult) capturedRequest.get().context().get("intentResult")).entities().get("order_id"));
    }
}
