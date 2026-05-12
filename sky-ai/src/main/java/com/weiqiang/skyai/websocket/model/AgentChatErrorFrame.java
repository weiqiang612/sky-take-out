package com.weiqiang.skyai.websocket.model;

public record AgentChatErrorFrame(
        String type,
        String message
) {
}
