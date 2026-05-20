package com.weiqiang.skyai.websocket;

import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.intent_recognition.service.CustomerIntentRecognitionService;
import com.weiqiang.skyai.advisor.IntentRecognitionAdvisor;
import com.weiqiang.skyai.advisor.RagAdvisor;
import com.weiqiang.skyai.advisor.SafeToolCallAdvisor;
import com.weiqiang.skyai.advisor.ToolFilterAdvisor;
import com.weiqiang.skyai.advisor.UserContextAdvisor;
import com.weiqiang.skyai.memory.service.ChatHistoryService;
import com.weiqiang.skyai.memory.service.MemoryWriterService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.StreamResponseSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentChatServiceTests {

    @Test
    void confirmedIntentShouldDisableConfirmation() {
        AgentChatService service = new AgentChatService(
                mock(ChatClient.Builder.class),
                mock(IntentRecognitionAdvisor.class),
                mock(UserContextAdvisor.class),
                mock(MessageChatMemoryAdvisor.class),
                mock(RagAdvisor.class),
                mock(ToolFilterAdvisor.class),
                mock(SafeToolCallAdvisor.class),
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
                mock(RagAdvisor.class),
                mock(ToolFilterAdvisor.class),
                mock(SafeToolCallAdvisor.class),
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
        assertEquals("Canceling this order will stop fulfillment.", service.confirmationFrame(result).question());
        assertEquals("Canceling this order will stop fulfillment.", service.confirmationFrame(result).reason());
    }

    @Test
    void confirmedIntentShouldRejectInvalidValue() {
        AgentChatService service = new AgentChatService(
                mock(ChatClient.Builder.class),
                mock(IntentRecognitionAdvisor.class),
                mock(UserContextAdvisor.class),
                mock(MessageChatMemoryAdvisor.class),
                mock(RagAdvisor.class),
                mock(ToolFilterAdvisor.class),
                mock(SafeToolCallAdvisor.class),
                mock(MemoryWriterService.class),
                mock(CustomerIntentRecognitionService.class),
                mock(ChatHistoryService.class)
        );

        assertThrows(IllegalArgumentException.class, () -> service.confirmedIntent("not-a-real-intent"));
    }

    @Test
    void streamChatShouldTimeoutAfterThirtySeconds() {
        ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
        StreamResponseSpec streamResponseSpec = mock(StreamResponseSpec.class);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.advisors(org.mockito.ArgumentMatchers.<Advisor[]>any())).thenReturn(requestSpec);
        when(requestSpec.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any())).thenReturn(requestSpec);
        when(requestSpec.toolContext(anyMap())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.chatClientResponse()).thenReturn(Flux.never());

        AgentChatService service = new AgentChatService(
                chatClientBuilder,
                mock(IntentRecognitionAdvisor.class),
                mock(UserContextAdvisor.class),
                mock(MessageChatMemoryAdvisor.class),
                mock(RagAdvisor.class),
                mock(ToolFilterAdvisor.class),
                mock(SafeToolCallAdvisor.class),
                mock(MemoryWriterService.class),
                mock(CustomerIntentRecognitionService.class),
                mock(ChatHistoryService.class)
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.applyStreamTimeout(Flux.never(), immediateScheduler()).next().block(Duration.ofSeconds(1))
        );
        assertEquals("Agent stream timed out after 30s", ex.getMessage());
        assertInstanceOf(TimeoutException.class, ex.getCause());
    }

    @Test
    void advisorsShouldUseRagAdvisorForFaqIntent() {
        RagAdvisor ragAdvisor = mock(RagAdvisor.class);
        AgentChatService service = new AgentChatService(
                mock(ChatClient.Builder.class),
                mock(IntentRecognitionAdvisor.class),
                mock(UserContextAdvisor.class),
                mock(MessageChatMemoryAdvisor.class),
                ragAdvisor,
                mock(ToolFilterAdvisor.class),
                mock(SafeToolCallAdvisor.class),
                mock(MemoryWriterService.class),
                mock(CustomerIntentRecognitionService.class),
                mock(ChatHistoryService.class)
        );

        IntentRecognitionResult faq = new IntentRecognitionResult(
                IntentType.FAQ,
                ConfidenceLevel.HIGH,
                Map.of(),
                List.of(IntentType.FAQ),
                null,
                false,
                null
        );

        List<CallAdvisor> advisors = service.advisors(faq);

        assertEquals(6, advisors.size());
        assertInstanceOf(RagAdvisor.class, advisors.get(3));
        assertEquals(ragAdvisor, advisors.get(3));
        assertFalse(advisors.stream().anyMatch(advisor -> advisor instanceof org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor));
        assertNotNull(advisors.get(4));
    }

    private Scheduler immediateScheduler() {
        return new Scheduler() {
            @Override
            public Disposable schedule(Runnable task) {
                task.run();
                return () -> {
                };
            }

            @Override
            public Disposable schedule(Runnable task, long delay, TimeUnit unit) {
                task.run();
                return () -> {
                };
            }

            @Override
            public Worker createWorker() {
                return new Worker() {
                    @Override
                    public Disposable schedule(Runnable task) {
                        task.run();
                        return () -> {
                        };
                    }

                    @Override
                    public Disposable schedule(Runnable task, long delay, TimeUnit unit) {
                        task.run();
                        return () -> {
                        };
                    }

                    @Override
                    public void dispose() {
                    }

                    @Override
                    public boolean isDisposed() {
                        return false;
                    }
                };
            }

            @Override
            public void dispose() {
            }

            @Override
            public boolean isDisposed() {
                return false;
            }
        };
    }
}
