package com.weiqiang.skyai.websocket.model;

public record AgentChatPlanCompleteFrame(
        String type,
        String summary,
        int steps
) {
}
