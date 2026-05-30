package com.weiqiang.skyai.rag.online.controller;

import com.weiqiang.skyai.annotation.RateLimit;
import com.weiqiang.skyai.rag.online.model.RagChatRequest;
import com.weiqiang.skyai.rag.online.model.RagChatResponse;
import com.weiqiang.skyai.rag.online.service.RagChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rag/online")
@RequiredArgsConstructor
public class RagChatController {

    private final RagChatService ragChatService;

    @RateLimit
    @GetMapping("/chat")
    public RagChatResponse chat(@RequestParam("question") String question) {
        return ragChatService.chat(new RagChatRequest(question));
    }

    @RateLimit
    @PostMapping("/chat")
    public RagChatResponse chat(@RequestBody RagChatRequest request) {
        return ragChatService.chat(request);
    }
}
