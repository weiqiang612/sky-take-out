package com.weiqiang.skyai.controller;

import com.weiqiang.skyai.rag.online.model.RetrievalResult;
import com.weiqiang.skyai.rag.online.service.OnlineRetrievalService;
import com.weiqiang.skyai.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RagController {

//    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final ChatClient.Builder gptChatClient;
    private final DocumentIngestionService documentIngestionService;
    private final OnlineRetrievalService onlineRetrievalService;


    @GetMapping("/ask")
    public Map<String, String> ask(@RequestParam("question") String question) {
        RetrievalResult retrieve = onlineRetrievalService.retrieve(question);
        log.info("在线检索完成，query={}，最终上下文=\n{}", question, retrieve.context());

        String systemPrompt = """
                你是一个有帮助的 AI 助手。
                    请根据以下提供的上下文信息来回答用户的问题。
                    如果上下文内容与问题无关，请说明你不知道，不要胡乱编造。
                
                    上下文：
                    {context}
                """;


//        ChatClient chatClient = chatClientBuilderProvider.getObject()
//                .build();

        String answer = gptChatClient.build()
                .prompt()
                .system(s -> s.text(systemPrompt).param("context", retrieve.context()))
                .user(question)
                .call()
                .content();

        return Map.of(
                "question", question,
                "answer", answer
        );
    }

    @PostMapping("/ingest-demo")
    public Map<String, Object> ingestDemoDocument() {
        int documentCount = documentIngestionService.ingestDemoDocument();
        return Map.of(
                "status", "ok",
                "documentsIngested", documentCount
        );
    }
}
