package com.weiqiang.skyai.intent_recognition.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IntentRecognitionResult(
        @JsonProperty("intent") IntentType intent,
        @JsonProperty("confidence") ConfidenceLevel confidence,
        @JsonProperty("entities") Map<String, String> entities,
        @JsonProperty("possible_intents") List<IntentType> possibleIntents,
        @JsonProperty("clarification_question") String clarificationQuestion,
        @JsonProperty("requires_human_confirmation") boolean requiresHumanConfirmation,
        @JsonProperty("human_confirmation_reason") String humanConfirmationReason
) {
}
