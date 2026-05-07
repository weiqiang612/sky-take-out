package com.weiqiang.skyai.config;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/5/6 16:55
 */

@Configuration
@RequiredArgsConstructor
public class AiConfig {

    private final VectorStore vectorStore;

    @Bean
    public ChatClient.Builder gptChatClient(ChatModel openAiChatModel) {
        // 专门用于对话的 Client Builder
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).build());
    }

}
