package com.weiqiang.skyai.websocket.model;

/**
 * WebSocket 错误响应数据帧
 */
public record AgentChatErrorFrame(
        String type,
        String message,
        Long retryAfterSeconds
) {
    /**
     * 向后兼容的构造函数，只带 type 和 message，默认将 retryAfterSeconds 置为 null
     */
    public AgentChatErrorFrame(String type, String message) {
        this(type, message, null);
    }
}
