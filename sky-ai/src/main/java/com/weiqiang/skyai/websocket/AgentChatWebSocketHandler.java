package com.weiqiang.skyai.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.websocket.model.AgentChatConfirmationFrame;
import com.weiqiang.skyai.websocket.model.AgentChatDoneFrame;
import com.weiqiang.skyai.websocket.model.AgentChatErrorFrame;
import com.weiqiang.skyai.websocket.model.AgentChatRequest;
import com.weiqiang.skyai.websocket.model.AgentChatTokenFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class AgentChatWebSocketHandler extends TextWebSocketHandler {

    private static final String PENDING_QUESTION_KEY = "pendingQuestion";
    private static final String PENDING_INTENT_KEY = "pendingIntent";
    private static final String ACTIVE_SUBSCRIPTION_KEY = "activeSubscription";

    private final AgentChatService agentChatService;
    private final ObjectMapper objectMapper;

    public AgentChatWebSocketHandler(AgentChatService agentChatService, ObjectMapper objectMapper) {
        this.agentChatService = agentChatService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            AgentChatRequest request = objectMapper.readValue(message.getPayload(), AgentChatRequest.class);
            if (request.isConfirmation()) {
                handleConfirmation(session, request);
                return;
            }
            handleQuestion(session, request);
        } catch (Exception ex) {
            sendError(session, "Failed to process chat message");
            log.error("Failed to process websocket message", ex);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        disposeActiveSubscription(session);
        session.getAttributes().remove(PENDING_QUESTION_KEY);
        session.getAttributes().remove(PENDING_INTENT_KEY);
    }

    private void handleQuestion(WebSocketSession session, AgentChatRequest request) {
        if (!StringUtils.hasText(request.message())) {
            sendError(session, "Message must not be empty");
            return;
        }
        String conversationId = safeText(request.conversationId());
        String userId = safeText(request.userId());
        log.info("Received question: {}, conversationId: {}, userId: {}", request.message(), conversationId, userId);
        if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(userId)) {
            sendError(session, "conversationId and userId are required");
            return;
        }
        IntentRecognitionResult preIntent = agentChatService.recognizeIntent(request.message(), conversationId, userId);
        if (preIntent.requiresHumanConfirmation()) {
            session.getAttributes().put(PENDING_QUESTION_KEY, request.message());
            session.getAttributes().put(PENDING_INTENT_KEY, preIntent.intent().value());
            sendConfirmation(session, agentChatService.confirmationFrame(preIntent));
            return;
        }
        startStreaming(session, request.message(), conversationId, userId, preIntent);
    }

    private void handleConfirmation(WebSocketSession session, AgentChatRequest request) {
        String pendingQuestion = safeText((String) session.getAttributes().get(PENDING_QUESTION_KEY));
        String pendingIntent = safeText((String) session.getAttributes().get(PENDING_INTENT_KEY));
        String conversationId = safeText(request.conversationId());
        String userId = safeText(request.userId());
        if (!StringUtils.hasText(pendingQuestion) || !StringUtils.hasText(pendingIntent)
                || !StringUtils.hasText(conversationId) || !StringUtils.hasText(userId)) {
            sendError(session, "No pending confirmation exists");
            return;
        }
        String requestedIntent = safeText(request.intent());
        if (!pendingIntent.equalsIgnoreCase(requestedIntent)) {
            sendError(session, "Confirmation intent does not match the pending request");
            return;
        }
        IntentRecognitionResult confirmedIntent;
        try {
            confirmedIntent = agentChatService.confirmedIntent(requestedIntent);
        } catch (Exception ex) {
            sendError(session, "Invalid confirmation intent");
            return;
        }
        session.getAttributes().remove(PENDING_QUESTION_KEY);
        session.getAttributes().remove(PENDING_INTENT_KEY);
        startStreaming(session, pendingQuestion, conversationId, userId, confirmedIntent);
    }

    private void startStreaming(WebSocketSession session,
                                String question,
                                String conversationId,
                                String userId,
                                IntentRecognitionResult preIntent) {
        log.info("走到了startSteaming...");
        // 在开始新的流式聊天之前，先检查并清理掉当前会话中可能存在的任何未完成的流式订阅，以避免资源泄漏和重复响应的问题。
        disposeActiveSubscription(session);
        // 使用AtomicReference来保存上一次收到的完整文本和最终确认的意图结果，以便在处理每个增量响应时能够正确计算出新的增量内容并在流式过程中持续更新最终的意图结果。
        AtomicReference<String> lastText = new AtomicReference<>("");
        // 初始时将预先识别的意图结果设置为最终意图，随着流式响应的进行，如果AI模型在上下文中返回了新的意图结果，就会更新这个AtomicReference，以确保在聊天完成时能够写入正确的最终意图结果到聊天记录中，并发送给前端。
        AtomicReference<IntentRecognitionResult> finalIntent = new AtomicReference<>(preIntent);
        Disposable subscription = agentChatService.streamChat(question, conversationId, userId, preIntent)
                .subscribe(
                        // 每次收到AI响应的增量内容时，都会调用handleStreamChunk方法来处理并发送给前端
                        response -> handleStreamChunk(session, response, lastText, finalIntent),
                        // 如果在流式过程中发生错误，会调用handleStreamError方法来处理错误并通知前端
                        ex -> handleStreamError(session, ex),
                        // 当流式完成时（无论是正常完成还是发生错误），都会调用这个回调来进行清理工作，比如写入聊天记录和发送完成消息
                        () -> {
                            try {
                                agentChatService.writeTurn(userId, conversationId, finalIntent.get());
                                sendDone(session, finalIntent.get());
                            } finally {
                                session.getAttributes().remove(ACTIVE_SUBSCRIPTION_KEY);
                            }
                        });
        session.getAttributes().put(ACTIVE_SUBSCRIPTION_KEY, subscription);
    }

    private void handleStreamChunk(WebSocketSession session,
                                   ChatClientResponse response,
                                   AtomicReference<String> lastText,
                                   AtomicReference<IntentRecognitionResult> finalIntent) {
        if (response != null && response.context() != null) {
            Object intent = response.context().get("intentResult");
            if (intent instanceof IntentRecognitionResult intentResult) {
                finalIntent.set(intentResult);
            }
        }
        String currentText = extractText(response);
        String previousText = lastText.getAndSet(currentText);
        String delta = extractDelta(previousText, currentText);
        if (StringUtils.hasText(delta)) {
            sendToken(session, delta);
        }
    }

    private void handleStreamError(WebSocketSession session, Throwable ex) {
        sendError(session, "AI response failed");
        session.getAttributes().remove(ACTIVE_SUBSCRIPTION_KEY);
        log.error("WebSocket chat stream failed", ex);
    }

    private void sendConfirmation(WebSocketSession session, AgentChatConfirmationFrame frame) {
        send(session, frame);
    }

    private void sendToken(WebSocketSession session, String content) {
        send(session, new AgentChatTokenFrame("token", content));
    }

    private void sendDone(WebSocketSession session, @Nullable IntentRecognitionResult intentResult) {
        send(session, new AgentChatDoneFrame("done", intentResult == null ? null : intentResult.intent().value()));
    }

    private void sendError(WebSocketSession session, String message) {
        send(session, new AgentChatErrorFrame("error", message));
    }

    private void send(WebSocketSession session, Object payload) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        } catch (Exception ex) {
            log.error("Failed to send websocket frame", ex);
        }
    }

    /**
     * 在开始新的流式聊天之前，先检查并清理掉当前会话中可能存在的任何未完成的流式订阅，以避免资源泄漏和重复响应的问题。
     * 这个方法会从WebSocketSession的属性中获取当前活跃的订阅对象，如果存在且是一个Disposable实例，就调用它的dispose方法来取消订阅，并从属性中移除这个键值对。
     */
    private void disposeActiveSubscription(WebSocketSession session) {
        Object active = session.getAttributes().remove(ACTIVE_SUBSCRIPTION_KEY);
        if (active instanceof Disposable disposable) {
            disposable.dispose();
        }
    }

    private String extractText(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null || response.chatResponse().getResult() == null
                || response.chatResponse().getResult().getOutput() == null) {
            return "";
        }
        String text = response.chatResponse().getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private String extractDelta(String previousText, String currentText) {
        if (!StringUtils.hasText(currentText)) {
            return "";
        }
        if (!StringUtils.hasText(previousText)) {
            return currentText;
        }
        if (currentText.startsWith(previousText)) {
            return currentText.substring(previousText.length());
        }
        return currentText;
    }

    private String safeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
