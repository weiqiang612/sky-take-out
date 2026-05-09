package com.weiqiang.skyai.intent_recognition.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IntentRecognitionRequest(
        @JsonProperty("message") String message,
        @JsonProperty("conversation_history")
        @JsonAlias("conversationHistory")
        List<String> conversationHistory
) {
}
