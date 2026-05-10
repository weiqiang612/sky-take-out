package com.weiqiang.skyai.memory.advisor;

import com.weiqiang.skyai.intent_recognition.client.CustomerIntentRecognitionClient;
import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionRequest;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.memory.model.UserMemory;
import com.weiqiang.skyai.memory.repository.RedisChatMemoryRepository;
import com.weiqiang.skyai.memory.repository.UserMemoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advisor for recognizing user intents in chat messages.
 */
@Slf4j
@Component
public class IntentRecognitionAdvisor implements CallAdvisor {

    private static final String INTENT_RESULT_KEY = "intentResult";
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("order[_\\s-]?id[:=\\s]+([a-zA-Z0-9-]+)", Pattern.CASE_INSENSITIVE);

    private final CustomerIntentRecognitionClient customerIntentRecognitionClient;
    private final RedisChatMemoryRepository redisChatMemoryRepository;
    private final UserMemoryRepository userMemoryRepository;

    public IntentRecognitionAdvisor(CustomerIntentRecognitionClient customerIntentRecognitionClient,
                                    RedisChatMemoryRepository redisChatMemoryRepository,
                                    UserMemoryRepository userMemoryRepository) {
        this.customerIntentRecognitionClient = customerIntentRecognitionClient;
        this.redisChatMemoryRepository = redisChatMemoryRepository;
        this.userMemoryRepository = userMemoryRepository;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        String conversationId = stringParam(chatClientRequest, ChatMemory.CONVERSATION_ID, ChatMemory.DEFAULT_CONVERSATION_ID);
        String userId = stringParam(chatClientRequest, "userId", null);
        String userText = chatClientRequest.prompt().getUserMessage().getText();

        IntentRecognitionResult result = customerIntentRecognitionClient.recognize(new IntentRecognitionRequest(userText, null));
        if (result != null && result.confidence() != ConfidenceLevel.LOW) {
            result = customerIntentRecognitionClient.recognize(new IntentRecognitionRequest(userText, buildHistory(conversationId, userId)));
        }
        if (result == null) {
            result = new IntentRecognitionResult(IntentType.OTHER, ConfidenceLevel.LOW, Map.of(), List.of(), null, false, null);
        }

        Map<String, Object> context = new HashMap<>(chatClientRequest.context());
        context.put(INTENT_RESULT_KEY, result);
        return callAdvisorChain.nextCall(chatClientRequest.mutate().context(context).build());
    }

    @Override
    public String getName() {
        return "intentRecognitionAdvisor";
    }

    // Place this advisor early in the chain to ensure intent recognition is available for subsequent advisors
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private List<String> buildHistory(String conversationId, String userId) {
        List<String> history = new ArrayList<>();
        List<org.springframework.ai.chat.messages.Message> messages = redisChatMemoryRepository.findByConversationId(conversationId);
        int start = Math.max(0, messages.size() - 4);
        for (int i = start; i < messages.size(); i++) {
            history.add(messages.get(i).getMessageType() + ": " + safeText(messages.get(i).getText()));
        }
        String orderId = extractOrderId(messages);
        if (StringUtils.hasText(orderId)) {
            history.add("Known order id: " + orderId);
        }
        if (StringUtils.hasText(userId)) {
            UserMemory userMemory = userMemoryRepository.findById(userId).orElse(null);
            if (userMemory != null && StringUtils.hasText(userMemory.getKnownIssues())) {
                history.add("Known issues: " + oneSentence(userMemory.getKnownIssues()));
            }
        }
        return history;
    }

    private String extractOrderId(List<org.springframework.ai.chat.messages.Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Matcher matcher = ORDER_ID_PATTERN.matcher(safeText(messages.get(i).getText()));
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private String oneSentence(String text) {
        int end = text.indexOf('.');
        return end >= 0 ? text.substring(0, end + 1).trim() : text.trim();
    }

    private String safeText(String text) {
        return StringUtils.hasText(text) ? text.trim() : "";
    }

    private String stringParam(ChatClientRequest request, String key, String fallback) {
        Object value = request.context().get(key);
        return value instanceof String stringValue && StringUtils.hasText(stringValue) ? stringValue : fallback;
    }
}
