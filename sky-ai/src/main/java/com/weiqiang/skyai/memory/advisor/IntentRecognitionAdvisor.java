package com.weiqiang.skyai.memory.advisor;

import com.weiqiang.skyai.intent_recognition.client.CustomerIntentRecognitionClient;
import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionRequest;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.memory.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Advisor for recognizing user intents in chat messages.
 */
@Slf4j
@Component
public class IntentRecognitionAdvisor implements CallAdvisor {

    private static final String INTENT_RESULT_KEY = "intentResult";
    private final CustomerIntentRecognitionClient customerIntentRecognitionClient;
    private final ChatHistoryService chatHistoryService;

    public IntentRecognitionAdvisor(CustomerIntentRecognitionClient customerIntentRecognitionClient,
                                    ChatHistoryService chatHistoryService) {
        this.customerIntentRecognitionClient = customerIntentRecognitionClient;
        this.chatHistoryService = chatHistoryService;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        // 优先使用 controller 预识别的结果，避免重复调用意图识别服务
        IntentRecognitionResult result = (IntentRecognitionResult) chatClientRequest.context().get("preRecognizedIntent");
        if (result == null) {
            String conversationId = stringParam(chatClientRequest, ChatMemory.CONVERSATION_ID, ChatMemory.DEFAULT_CONVERSATION_ID);
            String userId = stringParam(chatClientRequest, "userId", null);
            String userText = chatClientRequest.prompt().getUserMessage().getText();
            result = customerIntentRecognitionClient.recognize(new IntentRecognitionRequest(userText, chatHistoryService.buildHistory(conversationId, userId)));
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

    // buildHistory/related helpers moved to ChatHistoryService

    private String stringParam(ChatClientRequest request, String key, String fallback) {
        Object value = request.context().get(key);
        return value instanceof String stringValue && StringUtils.hasText(stringValue) ? stringValue : fallback;
    }
}
