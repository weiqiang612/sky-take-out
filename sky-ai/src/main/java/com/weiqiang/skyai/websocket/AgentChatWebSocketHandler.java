package com.weiqiang.skyai.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.websocket.model.AgentChatCancelledFrame;
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class AgentChatWebSocketHandler extends TextWebSocketHandler {

    private static final String PENDING_QUESTION_KEY = "pendingQuestion";
    private static final String PENDING_INTENT_KEY = "pendingIntent";
    private static final String ACTIVE_CONVERSATION_ID_KEY = "activeConversationId";
    private static final String CANCEL_TYPE = "cancel";

    private final AgentChatService agentChatService;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Disposable> activeStreams = new ConcurrentHashMap<>();

    public AgentChatWebSocketHandler(AgentChatService agentChatService, ObjectMapper objectMapper) {
        this.agentChatService = agentChatService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode frame = objectMapper.readTree(message.getPayload());
            String type = safeText(frame.path("type").asText(""));
            if (CANCEL_TYPE.equals(type)) {
                handleCancel(session, safeText(frame.path("conversationId").asText("")));
                return;
            }
            AgentChatRequest request = objectMapper.treeToValue(frame, AgentChatRequest.class);
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
        String conversationId = safeText((String) session.getAttributes().remove(ACTIVE_CONVERSATION_ID_KEY));
        disposeActiveSubscription(conversationId);
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
        if (preIntent.intent() == IntentType.OTHER) {
            sendOtherResponse(session, preIntent);
            agentChatService.writeTurn(userId, conversationId, preIntent);
            return;
        }
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
        disposeActiveSubscription(conversationId);
        AtomicReference<String> lastText = new AtomicReference<>("");
        AtomicReference<IntentRecognitionResult> finalIntent = new AtomicReference<>(preIntent);
        Disposable subscription = agentChatService.streamChat(question, conversationId, userId, preIntent)
                .subscribe(
                        response -> handleStreamChunk(session, response, lastText, finalIntent),
                        ex -> handleStreamError(session, conversationId, ex),
                        () -> handleStreamComplete(session, conversationId, userId, finalIntent.get())
                );
        if (subscription.isDisposed()) {
            session.getAttributes().remove(ACTIVE_CONVERSATION_ID_KEY);
            return;
        }
        activeStreams.put(conversationId, subscription);
        session.getAttributes().put(ACTIVE_CONVERSATION_ID_KEY, conversationId);
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

    private void handleStreamComplete(WebSocketSession session,
                                      String conversationId,
                                      String userId,
                                      @Nullable IntentRecognitionResult intentResult) {
        try {
            agentChatService.writeTurn(userId, conversationId, intentResult);
            sendDone(session, intentResult);
        } finally {
            disposeActiveSubscription(conversationId);
            session.getAttributes().remove(ACTIVE_CONVERSATION_ID_KEY);
        }
    }

    private void handleStreamError(WebSocketSession session, String conversationId, Throwable ex) {
        sendError(session, errorMessage(ex));
        disposeActiveSubscription(conversationId);
        session.getAttributes().remove(ACTIVE_CONVERSATION_ID_KEY);
        log.error("WebSocket chat stream failed", ex);
    }

    private void handleCancel(WebSocketSession session, String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            sendError(session, "conversationId is required");
            return;
        }
        disposeActiveSubscription(conversationId);
        String activeConversationId = safeText((String) session.getAttributes().get(ACTIVE_CONVERSATION_ID_KEY));
        if (conversationId.equals(activeConversationId)) {
            session.getAttributes().remove(ACTIVE_CONVERSATION_ID_KEY);
        }
        send(session, new AgentChatCancelledFrame("cancelled", conversationId));
    }

    private void sendConfirmation(WebSocketSession session, AgentChatConfirmationFrame frame) {
        send(session, frame);
    }

    private void sendToken(WebSocketSession session, String content) {
        send(session, new AgentChatTokenFrame("token", content));
    }

    private void sendOtherResponse(WebSocketSession session, IntentRecognitionResult intentResult) {
        sendToken(session, agentChatService.otherIntentResponse(intentResult));
        sendDone(session, intentResult);
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

    private void disposeActiveSubscription(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        Disposable disposable = activeStreams.remove(conversationId);
        if (disposable != null) {
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

    private String errorMessage(Throwable ex) {
        if (isTimeout(ex)) {
            return "本次回复超时了，请稍后重试，或换一种说法再试一次。";
        }
        return "AI response failed";
    }

    private boolean isTimeout(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
