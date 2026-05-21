package com.weiqiang.skyai.websocket.model;

public record AgentChatStepDoneFrame(
        String type,
        int step,
        String summary
) {
}
