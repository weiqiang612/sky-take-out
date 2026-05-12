package com.weiqiang.skyai.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AgentChatWebSocketHandler agentChatWebSocketHandler;

    public WebSocketConfig(AgentChatWebSocketHandler agentChatWebSocketHandler) {
        this.agentChatWebSocketHandler = agentChatWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentChatWebSocketHandler, "/ws/agent/chat")
                .setAllowedOriginPatterns("*");
    }
}
