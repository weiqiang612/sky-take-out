package com.weiqiang.skyai.websocket;

import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.intent_recognition.service.CustomerIntentRecognitionService;
import com.weiqiang.skyai.advisor.IntentRecognitionAdvisor;
import com.weiqiang.skyai.advisor.ToolFilterAdvisor;
import com.weiqiang.skyai.advisor.UserContextAdvisor;
import com.weiqiang.skyai.memory.service.ChatHistoryService;
import com.weiqiang.skyai.memory.service.MemoryWriterService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class AgentChatServiceTests {

    @Test
    void confirmedIntentShouldDisableConfirmation() {
        AgentChatService service = new AgentChatService(
                mock(ChatClient.Builder.class),
                mock(IntentRecognitionAdvisor.class),
                mock(UserContextAdvisor.class),
                mock(MessageChatMemoryAdvisor.class),
                mock(QuestionAnswerAdvisor.class),
                mock(ToolFilterAdvisor.class),
                mock(MemoryWriterService.class),
                mock(CustomerIntentRecognitionService.class),
                mock(ChatHistoryService.class)
        );

        IntentRecognitionResult confirmed = service.confirmedIntent("cancel_order");

        assertEquals(IntentType.CANCEL_ORDER, confirmed.intent());
        assertEquals(ConfidenceLevel.HIGH, confirmed.confidence());
        assertFalse(confirmed.requiresHumanConfirmation());
        assertNull(confirmed.humanConfirmationReason());
    }

    @Test
    void confirmationFrameShouldIncludeOrderIdAndQuestion() {
        AgentChatService service = new AgentChatService(
                mock(ChatClient.Builder.class),
                mock(IntentRecognitionAdvisor.class),
                mock(UserContextAdvisor.class),
                mock(MessageChatMemoryAdvisor.class),
                mock(QuestionAnswerAdvisor.class),
                mock(ToolFilterAdvisor.class),
                mock(MemoryWriterService.class),
                mock(CustomerIntentRecognitionService.class),
                mock(ChatHistoryService.class)
        );

        IntentRecognitionResult result = new IntentRecognitionResult(
                IntentType.CANCEL_ORDER,
                ConfidenceLevel.HIGH,
                Map.of("order_id", "12345"),
                List.of(IntentType.CANCEL_ORDER),
                null,
                true,
                "Canceling this order will stop fulfillment."
        );

        assertEquals("confirmation", service.confirmationFrame(result).type());
        assertEquals("12345", service.confirmationFrame(result).orderId());
        assertEquals("Do you want to cancel order 12345?", service.confirmationFrame(result).question());
        assertEquals("Canceling this order will stop fulfillment.", service.confirmationFrame(result).reason());
    }

    @Test
    void confirmedIntentShouldRejectInvalidValue() {
        AgentChatService service = new AgentChatService(
                mock(ChatClient.Builder.class),
                mock(IntentRecognitionAdvisor.class),
                mock(UserContextAdvisor.class),
                mock(MessageChatMemoryAdvisor.class),
                mock(QuestionAnswerAdvisor.class),
                mock(ToolFilterAdvisor.class),
                mock(MemoryWriterService.class),
                mock(CustomerIntentRecognitionService.class),
                mock(ChatHistoryService.class)
        );

        assertThrows(IllegalArgumentException.class, () -> service.confirmedIntent("not-a-real-intent"));
    }
}
