package com.weiqiang.skyai.config;

import com.weiqiang.skyai.tools.AddressTools;
import com.weiqiang.skyai.tools.CartTools;
import com.weiqiang.skyai.tools.MenuTools;
import com.weiqiang.skyai.tools.OrderTools;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AiConfig {

    private final VectorStore vectorStore;
    private final OrderTools orderTools;
    private final MenuTools menuTools;
    private final CartTools cartTools;
    private final AddressTools addressTools;

    @Bean
    public ChatClient.Builder gptChatClient(ChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                .defaultTools(orderTools, menuTools, cartTools, addressTools);
    }
}
