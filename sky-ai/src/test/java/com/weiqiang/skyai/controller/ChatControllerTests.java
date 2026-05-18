package com.weiqiang.skyai.controller;

import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.websocket.AgentChatService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerTests {

    @Test
    void shouldUseRagOnlyForFaqIntent() {
        ChatController controller = new ChatController(mock(AgentChatService.class));

        IntentRecognitionResult faq = new IntentRecognitionResult(
                IntentType.FAQ,
                ConfidenceLevel.HIGH,
                Map.of(),
                List.of(),
                null,
                false,
                null
        );
        IntentRecognitionResult menuQuery = new IntentRecognitionResult(
                IntentType.MENU_QUERY,
                ConfidenceLevel.HIGH,
                Map.of(),
                List.of(),
                null,
                false,
                null
        );

        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(controller, "shouldUseRag", faq));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(controller, "shouldUseRag", menuQuery));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(controller, "shouldUseRag", (Object) null));
    }

    @Test
    void shouldShortCircuitOtherIntentWithoutCallingAsk() {
        AgentChatService agentChatService = mock(AgentChatService.class);
        ChatController controller = new ChatController(agentChatService);

        IntentRecognitionResult other = new IntentRecognitionResult(
                IntentType.OTHER,
                ConfidenceLevel.HIGH,
                Map.of(),
                List.of(IntentType.OTHER),
                null,
                false,
                null
        );
        when(agentChatService.recognizeIntent("hello", "conv-1", "user-1")).thenReturn(other);
        when(agentChatService.otherIntentResponse(other)).thenReturn("请补充一下你的具体诉求。");

        Map<String, String> response = controller.ask("hello", "conv-1", "user-1");

        assertTrue(response.containsKey("answer"));
        assertTrue(response.get("answer").contains("补充"));
        verify(agentChatService, never()).ask(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }
}
