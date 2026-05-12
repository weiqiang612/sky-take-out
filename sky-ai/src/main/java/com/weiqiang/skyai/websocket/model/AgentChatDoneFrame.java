package com.weiqiang.skyai.websocket.model;

public record AgentChatDoneFrame(
        String type,
        String intent
) {
}
