package com.weiqiang.skyai.memory.config;

import com.weiqiang.skyai.memory.repository.RedisChatMemoryRepository;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Configuration class for setting up chat memory and related advisors.
 */
@Configuration
public class MemoryConfig {

    @Bean
    ChatMemory chatMemory(RedisChatMemoryRepository redisChatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    // Ensure the MessageChatMemoryAdvisor runs after intent recognition and user context advisors, but before the RAG advisor
    @Bean
    MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).order(Ordered.HIGHEST_PRECEDENCE + 2).build();
    }
}
