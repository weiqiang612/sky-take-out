package com.weiqiang.skyai.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.task.TaskOrchestratorService;
import com.weiqiang.skyai.task.model.TaskExecutionOutcome;
import com.weiqiang.skyai.task.model.TaskPlan;
import com.weiqiang.skyai.task.model.TaskStep;
import com.weiqiang.skyai.task.model.TaskPlanningResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentChatWebSocketHandlerTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void cancelFrameShouldDisposeActiveStreamAndAck() throws Exception {
        AgentChatService agentChatService = mock(AgentChatService.class);
        TaskOrchestratorService taskOrchestratorService = mock(TaskOrchestratorService.class);
        AgentChatWebSocketHandler handler = new AgentChatWebSocketHandler(agentChatService, taskOrchestratorService, objectMapper);
        WebSocketSession session = mockSession();
        List<String> frames = new ArrayList<>();
        captureFrames(session, frames);
        Disposable disposable = mock(Disposable.class);
        activeStreams(handler).put("conv-1", disposable);
        session.getAttributes().put("activeConversationId", "conv-1");

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"cancel\",\"conversationId\":\"conv-1\"}"));

        verify(disposable).dispose();
        assertEquals("cancelled", objectMapper.readTree(frames.get(0)).path("type").asText());
        assertEquals("conv-1", objectMapper.readTree(frames.get(0)).path("conversationId").asText());
        assertFalse(activeStreams(handler).containsKey("conv-1"));
        assertNull(session.getAttributes().get("activeConversationId"));
    }

    @Test
    void closeConnectionShouldDisposeActiveStream() throws Exception {
        AgentChatService agentChatService = mock(AgentChatService.class);
        TaskOrchestratorService taskOrchestratorService = mock(TaskOrchestratorService.class);
        AgentChatWebSocketHandler handler = new AgentChatWebSocketHandler(agentChatService, taskOrchestratorService, objectMapper);
        WebSocketSession session = mockSession();
        IntentRecognitionResult intentResult = new IntentRecognitionResult(
                IntentType.ORDER_STATUS,
                ConfidenceLevel.HIGH,
                Map.of(),
                List.of(IntentType.ORDER_STATUS),
                null,
                false,
                null
        );
        when(agentChatService.recognizeIntent(eq("where is my order"), eq("conv-2"), eq("user-1"))).thenReturn(intentResult);
        when(taskOrchestratorService.plan(any(), any(), any(), any())).thenReturn(TaskPlanningResult.notDecomposed());
        when(agentChatService.streamChat(eq("where is my order"), eq("conv-2"), eq("user-1"), any(IntentRecognitionResult.class)))
                .thenReturn(Flux.never());

        handler.handleTextMessage(session, new TextMessage("{\"conversationId\":\"conv-2\",\"userId\":\"user-1\",\"message\":\"where is my order\"}"));

        Disposable disposable = activeStreams(handler).get("conv-2");
        assertNotNull(disposable);
        assertFalse(disposable.isDisposed());

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertTrue(disposable.isDisposed());
        assertFalse(activeStreams(handler).containsKey("conv-2"));
        assertNull(session.getAttributes().get("activeConversationId"));
    }

    @Test
    void timeoutStreamShouldSendFriendlyErrorMessage() throws Exception {
        AgentChatService agentChatService = mock(AgentChatService.class);
        TaskOrchestratorService taskOrchestratorService = mock(TaskOrchestratorService.class);
        AgentChatWebSocketHandler handler = new AgentChatWebSocketHandler(agentChatService, taskOrchestratorService, objectMapper);
        WebSocketSession session = mockSession();
        List<String> frames = new ArrayList<>();
        captureFrames(session, frames);
        IntentRecognitionResult intentResult = new IntentRecognitionResult(
                IntentType.ORDER_STATUS,
                ConfidenceLevel.HIGH,
                Map.of(),
                List.of(IntentType.ORDER_STATUS),
                null,
                false,
                null
        );
        when(agentChatService.recognizeIntent(eq("where is my order"), eq("conv-3"), eq("user-1"))).thenReturn(intentResult);
        when(taskOrchestratorService.plan(any(), any(), any(), any())).thenReturn(TaskPlanningResult.notDecomposed());
        when(agentChatService.streamChat(eq("where is my order"), eq("conv-3"), eq("user-1"), any(IntentRecognitionResult.class)))
                .thenReturn(Flux.error(new IllegalStateException("Agent stream timed out after 30s", new TimeoutException())));

        handler.handleTextMessage(session, new TextMessage("{\"conversationId\":\"conv-3\",\"userId\":\"user-1\",\"message\":\"where is my order\"}"));

        assertEquals("error", objectMapper.readTree(frames.get(0)).path("type").asText());
        assertEquals("本次回复超时了，请稍后重试，或换一种说法再试一次。", objectMapper.readTree(frames.get(0)).path("message").asText());
        assertFalse(activeStreams(handler).containsKey("conv-3"));
    }

    @Test
    void otherIntentShouldReturnSafeClarificationWithoutStreaming() throws Exception {
        AgentChatService agentChatService = mock(AgentChatService.class);
        TaskOrchestratorService taskOrchestratorService = mock(TaskOrchestratorService.class);
        AgentChatWebSocketHandler handler = new AgentChatWebSocketHandler(agentChatService, taskOrchestratorService, objectMapper);
        WebSocketSession session = mockSession();
        List<String> frames = new ArrayList<>();
        captureFrames(session, frames);
        IntentRecognitionResult intentResult = new IntentRecognitionResult(
                IntentType.OTHER,
                ConfidenceLevel.HIGH,
                Map.of(),
                List.of(IntentType.OTHER),
                null,
                false,
                null
        );
        when(agentChatService.recognizeIntent(eq("hello"), eq("conv-4"), eq("user-1"))).thenReturn(intentResult);
        when(agentChatService.otherIntentResponse(intentResult)).thenReturn("请补充一下你的具体诉求。");

        handler.handleTextMessage(session, new TextMessage("{\"conversationId\":\"conv-4\",\"userId\":\"user-1\",\"message\":\"hello\"}"));

        assertEquals("token", objectMapper.readTree(frames.get(0)).path("type").asText());
        assertEquals("请补充一下你的具体诉求。", objectMapper.readTree(frames.get(0)).path("content").asText());
        assertEquals("done", objectMapper.readTree(frames.get(1)).path("type").asText());
        assertEquals("other", objectMapper.readTree(frames.get(1)).path("intent").asText());
        assertFalse(activeStreams(handler).containsKey("conv-4"));
        assertNull(session.getAttributes().get("activeConversationId"));
        assertNull(session.getAttributes().get("pendingQuestion"));
        assertNull(session.getAttributes().get("pendingIntent"));
        verify(agentChatService, never()).streamChat(any(), any(), any(), any(IntentRecognitionResult.class));
    }

    @Test
    void confirmationShouldReenterPlanExecutionForBatchCancel() throws Exception {
        AgentChatService agentChatService = mock(AgentChatService.class);
        TaskOrchestratorService taskOrchestratorService = mock(TaskOrchestratorService.class);
        AgentChatWebSocketHandler handler = new AgentChatWebSocketHandler(agentChatService, taskOrchestratorService, objectMapper);
        WebSocketSession session = mockSession();
        List<String> frames = new ArrayList<>();
        captureFrames(session, frames);

        IntentRecognitionResult pendingIntentResult = new IntentRecognitionResult(
                IntentType.CANCEL_ORDER,
                ConfidenceLevel.HIGH,
                Map.of("order_ids", "1779351452612,1779341664613"),
                List.of(IntentType.CANCEL_ORDER),
                null,
                true,
                "请确认取消这两个订单。"
        );
        IntentRecognitionResult confirmedIntent = new IntentRecognitionResult(
                IntentType.CANCEL_ORDER,
                ConfidenceLevel.HIGH,
                Map.of("order_ids", "1779351452612,1779341664613"),
                List.of(IntentType.CANCEL_ORDER),
                null,
                false,
                null
        );
        TaskPlan plan = new TaskPlan("p-batch", List.of(
                new TaskStep(1, IntentType.CANCEL_ORDER, Map.of("order_id", "1779351452612"), false, "请取消目标订单。 订单号：1779351452612。"),
                new TaskStep(2, IntentType.CANCEL_ORDER, Map.of("order_id", "1779341664613"), false, "请取消目标订单。 订单号：1779341664613。")
        ));

        when(agentChatService.confirmedIntent(eq("cancel_order"), eq(pendingIntentResult))).thenReturn(confirmedIntent);
        when(taskOrchestratorService.plan(eq("取消这两个订单吧"), eq("conv-5"), eq("user-1"), any(IntentRecognitionResult.class)))
                .thenReturn(new TaskPlanningResult(true, plan));
        when(taskOrchestratorService.executePlan(eq("取消这两个订单吧"), eq("conv-5"), eq("user-1"), any(IntentRecognitionResult.class), eq(plan)))
                .thenReturn(new TaskExecutionOutcome(true, false, "两个订单已取消", null, null, List.of("已取消订单1779351452612", "已取消订单1779341664613")));

        session.getAttributes().put("pendingQuestion", "取消这两个订单吧");
        session.getAttributes().put("pendingIntent", "cancel_order");
        session.getAttributes().put("pendingIntentResult", pendingIntentResult);

        handler.handleTextMessage(session, new TextMessage("{\"conversationId\":\"conv-5\",\"userId\":\"user-1\",\"confirmation\":true,\"intent\":\"cancel_order\"}"));

        assertEquals("step_start", objectMapper.readTree(frames.get(0)).path("type").asText());
        assertTrue(frames.stream().anyMatch(frame -> {
            try {
                return "plan_complete".equals(objectMapper.readTree(frame).path("type").asText());
            } catch (Exception ex) {
                return false;
            }
        }));
        assertEquals("done", objectMapper.readTree(frames.get(frames.size() - 1)).path("type").asText());
        verify(taskOrchestratorService).plan(eq("取消这两个订单吧"), eq("conv-5"), eq("user-1"), any(IntentRecognitionResult.class));
        verify(taskOrchestratorService).executePlan(eq("取消这两个订单吧"), eq("conv-5"), eq("user-1"), any(IntentRecognitionResult.class), eq(plan));
        verify(agentChatService, never()).streamChat(any(), any(), any(), any(IntentRecognitionResult.class));
    }

    private WebSocketSession mockSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new ConcurrentHashMap<>());
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private void captureFrames(WebSocketSession session, List<String> frames) throws Exception {
        doAnswer(invocation -> {
            TextMessage message = invocation.getArgument(0);
            frames.add(message.getPayload());
            return null;
        }).when(session).sendMessage(any(TextMessage.class));
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, Disposable> activeStreams(AgentChatWebSocketHandler handler) throws Exception {
        Field field = AgentChatWebSocketHandler.class.getDeclaredField("activeStreams");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, Disposable>) field.get(handler);
    }
}
