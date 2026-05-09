package com.weiqiang.skyai.rag.online.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatClientQueryExpansionClient implements QueryExpansionClient {

    private static final String SYSTEM_PROMPT = """
            你是 RAG 检索查询改写器。请基于用户原始问题生成相关检索问题。
            要求：
            1. 只返回 JSON 字符串数组，不要解释。
            2. 每个问题必须保留原始问题中的核心专有名词、编号、英文词、接口名或关键词。
            3. 不要回答问题，不要编造上下文中不存在的事实。
            4. 最多返回 {maxQueries} 个问题。
            """;

    private final ChatClient.Builder gptChatClient;

    @Override
    public String generate(String query, int maxQueries) {
        return gptChatClient.build()
                .prompt()
                .system(s -> s.text(SYSTEM_PROMPT).param("maxQueries", maxQueries))
                .user(query)
                .call()
                .content();
    }
}
