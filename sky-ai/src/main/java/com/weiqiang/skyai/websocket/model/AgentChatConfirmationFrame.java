package com.weiqiang.skyai.websocket.model;

public record AgentChatConfirmationFrame(
        String type,
        String intent,
        String orderId,
        String question,
        String reason
) {
}
