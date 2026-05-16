package com.weiqiang.skyai.websocket.model;

public record AgentChatCancelledFrame(
        String type,
        String conversationId
) {
}
