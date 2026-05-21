package com.weiqiang.skyai.websocket.model;

public record AgentChatStepStartFrame(
        String type,
        int step,
        String intent
) {
}
