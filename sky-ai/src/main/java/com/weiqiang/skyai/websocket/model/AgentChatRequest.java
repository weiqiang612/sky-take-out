package com.weiqiang.skyai.websocket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentChatRequest(
        @JsonProperty("conversationId") String conversationId,
        @JsonProperty("userId") String userId,
        @JsonProperty("message") String message,
        @JsonProperty("confirmation") Boolean confirmation,
        @JsonProperty("intent") String intent
) {
    public boolean isConfirmation() {
        return Boolean.TRUE.equals(confirmation);
    }
}
