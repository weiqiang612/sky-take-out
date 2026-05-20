package com.weiqiang.skyai.intent_recognition.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IntentRecognitionResult(
        @JsonProperty("intent") IntentType intent,
        @JsonProperty("confidence") ConfidenceLevel confidence,
        @JsonProperty("entities") Map<String, String> entities,
        @JsonProperty("possible_intents") List<IntentType> possibleIntents,
        // 需要澄清的问题
        @JsonProperty("clarification_question") @Nullable String clarificationQuestion,
        // 需要二次确认
        @JsonProperty("requires_human_confirmation") boolean requiresHumanConfirmation,
        // 需要人工确认的原因
        @JsonProperty("human_confirmation_reason") @Nullable String humanConfirmationReason
) {
}
