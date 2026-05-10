package com.weiqiang.skyai.memory.config;

import com.weiqiang.skyai.memory.repository.RedisChatMemoryRepository;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.vectorstore.VectorStore;
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

    // Ensure the MessageChatMemoryAdvisor runs after intent recognition and user context advisors, but before the QuestionAnswerAdvisor
    @Bean
    MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).order(Ordered.HIGHEST_PRECEDENCE + 2).build();
    }

    // Ensure the QuestionAnswerAdvisor runs after the MessageChatMemoryAdvisor to leverage the chat history for better question answering
    @Bean
    QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        return QuestionAnswerAdvisor.builder(vectorStore).order(Ordered.HIGHEST_PRECEDENCE + 3).build();
    }
}
