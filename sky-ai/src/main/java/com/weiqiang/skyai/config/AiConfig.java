package com.weiqiang.skyai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiConfig {

    @Bean
    @Primary
    public ChatClient.Builder chatClientBuilder(ChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel);
    }

    @Bean
    public ChatClient.Builder toolChatClientBuilder(ChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel);
    }
}
