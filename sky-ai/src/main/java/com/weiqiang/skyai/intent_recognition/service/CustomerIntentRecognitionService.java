package com.weiqiang.skyai.intent_recognition.service;

import com.weiqiang.skyai.intent_recognition.client.CustomerIntentRecognitionClient;
import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionRequest;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerIntentRecognitionService {

    private static final String DEFAULT_CLARIFICATION_QUESTION = "可以补充一下你的具体诉求吗，例如订单号、商品或想处理的事项？";
    private final CustomerIntentRecognitionClient customerIntentRecognitionClient;

    public IntentRecognitionResult recognize(IntentRecognitionRequest request) {
        IntentRecognitionResult result = customerIntentRecognitionClient.recognize(request);
        IntentRecognitionResult normalized = normalize(result);

        log.info("intent recognition result: intent={}, confidence={}, entities={}, requiresHumanConfirmation={}",
                normalized.intent(),
                normalized.confidence(),
                normalized.entities(),
                normalized.requiresHumanConfirmation());

        return normalized;
    }

    private IntentRecognitionResult normalize(IntentRecognitionResult result) {
        if (result == null) {
            return buildFallback();
        }

        IntentType intent = result.intent() == null ? IntentType.OTHER : result.intent();
        ConfidenceLevel confidence = result.confidence() == null ? ConfidenceLevel.LOW : result.confidence();
        Map<String, String> entities = result.entities() == null ? Map.of() : new LinkedHashMap<>(result.entities());
        List<IntentType> possibleIntents = result.possibleIntents() == null ? List.of() : List.copyOf(result.possibleIntents());
        boolean requiresHumanConfirmation = result.requiresHumanConfirmation();
        String humanConfirmationReason = result.humanConfirmationReason();
        String clarificationQuestion = result.clarificationQuestion();

        if (!StringUtils.hasText(clarificationQuestion) && (confidence == ConfidenceLevel.LOW || intent == IntentType.OTHER)) {
            clarificationQuestion = DEFAULT_CLARIFICATION_QUESTION;
        }

        if (isHighRisk(intent) && !requiresHumanConfirmation) {
            boolean hasOrderId = entities != null && StringUtils.hasText(entities.getOrDefault("order_id", null));
            if (hasOrderId) {
                requiresHumanConfirmation = true;
                if (!StringUtils.hasText(humanConfirmationReason)) {
                    humanConfirmationReason = "该意图属于高风险操作，需要人工确认后再继续处理。";
                }
            }
        }

        return new IntentRecognitionResult(
                intent,
                confidence,
                entities,
                possibleIntents,
                clarificationQuestion,
                requiresHumanConfirmation,
                humanConfirmationReason
        );
    }

    private IntentRecognitionResult buildFallback() {
        return new IntentRecognitionResult(
                IntentType.OTHER,
                ConfidenceLevel.LOW,
                Map.of(),
                List.of(),
                DEFAULT_CLARIFICATION_QUESTION,
                false,
                null
        );
    }

    private boolean isHighRisk(IntentType intent) {
        return intent.isHighRisk();
    }
}
