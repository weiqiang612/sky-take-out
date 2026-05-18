package com.weiqiang.skyai.advisor;

import com.weiqiang.skyai.intent_recognition.client.CustomerIntentRecognitionClient;
import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionRequest;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.memory.config.UserProfileMemoryProperties;
import com.weiqiang.skyai.memory.service.ChatHistoryService;
import com.weiqiang.skyai.memory.service.UserMemoryFactService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
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
public class IntentRecognitionAdvisor implements CallAdvisor, StreamAdvisor {

    private static final String INTENT_RESULT_KEY = "intentResult";
    private final CustomerIntentRecognitionClient customerIntentRecognitionClient;
    private final ChatHistoryService chatHistoryService;
    private final UserMemoryFactService userMemoryFactService;
    private final UserProfileMemoryProperties userProfileMemoryProperties;
    private final UserProfileInjectionMetrics userProfileInjectionMetrics;

    public IntentRecognitionAdvisor(CustomerIntentRecognitionClient customerIntentRecognitionClient,
                                    ChatHistoryService chatHistoryService,
                                    UserMemoryFactService userMemoryFactService,
                                    UserProfileMemoryProperties userProfileMemoryProperties,
                                    UserProfileInjectionMetrics userProfileInjectionMetrics) {
        this.customerIntentRecognitionClient = customerIntentRecognitionClient;
        this.chatHistoryService = chatHistoryService;
        this.userMemoryFactService = userMemoryFactService;
        this.userProfileMemoryProperties = userProfileMemoryProperties;
        this.userProfileInjectionMetrics = userProfileInjectionMetrics;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        IntentRecognitionResult result = recognize(chatClientRequest);
        Map<String, Object> context = new HashMap<>(chatClientRequest.context());
        context.put(INTENT_RESULT_KEY, result);
        return callAdvisorChain.nextCall(chatClientRequest.mutate().context(context).build());
    }

    @Override
    public reactor.core.publisher.Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        IntentRecognitionResult result = recognize(chatClientRequest);
        Map<String, Object> context = new HashMap<>(chatClientRequest.context());
        context.put(INTENT_RESULT_KEY, result);
        return streamAdvisorChain.nextStream(chatClientRequest.mutate().context(context).build());
    }

    @Override
    public String getName() {
        return "intentRecognitionAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private IntentRecognitionResult recognize(ChatClientRequest chatClientRequest) {
        IntentRecognitionResult result = (IntentRecognitionResult) chatClientRequest.context().get("preRecognizedIntent");
        if (result != null) {
            return result;
        }

        String conversationId = stringParam(chatClientRequest, ChatMemory.CONVERSATION_ID, ChatMemory.DEFAULT_CONVERSATION_ID);
        String userId = stringParam(chatClientRequest, "userId", null);
        String userText = chatClientRequest.prompt().getUserMessage().getText();
        ProfileSummaryInjection profileSummaryInjection = injectProfileSummary(userId, userText);
        result = customerIntentRecognitionClient.recognize(
                new IntentRecognitionRequest(profileSummaryInjection.message(), chatHistoryService.buildHistory(conversationId, userId))
        );
        if (result == null) {
            result = new IntentRecognitionResult(IntentType.OTHER, ConfidenceLevel.LOW, Map.of(), List.of(), null, false, null);
        }
        userProfileInjectionMetrics.recordIntentRecognition(result.intent(), profileSummaryInjection.level(), profileSummaryInjection.injected(), profileSummaryInjection.charsInjected());
        log.debug("intent recognition profile summary intentType={} level={} injected={} charsInjected={}",
                result.intent(), profileSummaryInjection.level(), profileSummaryInjection.injected(), profileSummaryInjection.charsInjected());
        return result;
    }

    private ProfileSummaryInjection injectProfileSummary(String userId, String userText) {
        String safeUserText = userText == null ? "" : userText;
        if (!userProfileMemoryProperties.isEnabled() || !userProfileMemoryProperties.isIntentRecognitionSummaryEnabled()) {
            return new ProfileSummaryInjection(safeUserText, ProfileInjectionLevel.NONE, false, 0);
        }
        if (!StringUtils.hasText(userId)) {
            return new ProfileSummaryInjection(safeUserText, ProfileInjectionLevel.NONE, false, 0);
        }
        String summary = userMemoryFactService.userProfileNotesSummary(userId);
        if (!StringUtils.hasText(summary)) {
            return new ProfileSummaryInjection(safeUserText, ProfileInjectionLevel.NONE, false, 0);
        }
        String injected = "User profile notes: " + summary.trim();
        return new ProfileSummaryInjection(injected + "\n" + safeUserText, ProfileInjectionLevel.SUMMARY, true, summary.trim().length());
    }

    private String stringParam(ChatClientRequest request, String key, String fallback) {
        Object value = request.context().get(key);
        return value instanceof String stringValue && StringUtils.hasText(stringValue) ? stringValue : fallback;
    }

    private record ProfileSummaryInjection(String message, ProfileInjectionLevel level, boolean injected, int charsInjected) {
    }
}
