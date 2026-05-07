package com.weiqiang.skyai.controller;

import com.weiqiang.skyai.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RagController {

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final DocumentIngestionService documentIngestionService;

    @GetMapping("/ask")
    public Map<String, String> ask(@RequestParam("question") String question) {
        ChatClient chatClient = chatClientBuilderProvider.getObject()
                .build();

        String answer = chatClient.prompt()
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
