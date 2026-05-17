package com.weiqiang.skyai.rag.online.service;

import com.weiqiang.skyai.rag.online.model.RagChatRequest;
import com.weiqiang.skyai.rag.online.model.RagChatResponse;
import com.weiqiang.skyai.rag.online.model.RetrievalResult;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RagChatService {

    private static final String SYSTEM_PROMPT = """
            你是一个只依据检索上下文回答问题的 RAG 助手。
            规则：
            1. 只能使用 <context> 中的内容回答。
            2. 如果 <context> 为空，或者内容不足以回答问题，请直接回答：未找到相关知识库内容。
            3. 不要编造，不要补充外部知识，不要提到你在进行检索。
            4. 用中文简洁回答，除非用户明确要求使用其他语言。
            <context>
            {context}
            </context>
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final OnlineRetrievalService onlineRetrievalService;

    public RagChatResponse chat(RagChatRequest request) {
        String question = request == null ? null : request.question();
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("question is required");
        }

        RetrievalResult retrievalResult = onlineRetrievalService.retrieve(question);
        String answer = chatClientBuilder.build().prompt()
                .system(system -> system.text(SYSTEM_PROMPT).param("context", safeContext(retrievalResult.context())))
                .user(question)
                .call()
                .content();

        return new RagChatResponse(
                question,
                answer == null ? "" : answer,
                retrievalResult.context(),
                retrievalResult.chunks()
        );
    }

    private String safeContext(String context) {
        return context == null ? "" : context;
    }
}
