package com.weiqiang.skyai.advisor;

import com.weiqiang.skyai.rag.online.model.RetrievalResult;
import com.weiqiang.skyai.rag.online.service.OnlineRetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class RagAdvisor implements CallAdvisor, StreamAdvisor {

    private static final String RETRIEVAL_CONTEXT_PREFIX = "使用以下检索上下文回答问题：\n";

    private final OnlineRetrievalService onlineRetrievalService;

    public RagAdvisor(OnlineRetrievalService onlineRetrievalService) {
        this.onlineRetrievalService = onlineRetrievalService;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        return callAdvisorChain.nextCall(applyRetrievalContext(chatClientRequest));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        return streamAdvisorChain.nextStream(applyRetrievalContext(chatClientRequest));
    }

    @Override
    public String getName() {
        return "ragAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }

    private ChatClientRequest applyRetrievalContext(ChatClientRequest chatClientRequest) {
        String userText = extractUserText(chatClientRequest);
        if (!StringUtils.hasText(userText)) {
            return chatClientRequest;
        }

        RetrievalResult retrievalResult = onlineRetrievalService.retrieve(userText);
        if (retrievalResult == null || !StringUtils.hasText(retrievalResult.context())) {
            return chatClientRequest;
        }

        List<Message> instructions = new ArrayList<>(chatClientRequest.prompt().getInstructions());
        instructions.add(0, new SystemMessage(RETRIEVAL_CONTEXT_PREFIX + retrievalResult.context()));
        Prompt prompt = new Prompt(instructions, chatClientRequest.prompt().getOptions());
        return chatClientRequest.mutate().prompt(prompt).build();
    }

    private String extractUserText(ChatClientRequest chatClientRequest) {
        if (chatClientRequest == null || chatClientRequest.prompt() == null || chatClientRequest.prompt().getUserMessage() == null) {
            return null;
        }
        String text = chatClientRequest.prompt().getUserMessage().getText();
        return StringUtils.hasText(text) ? text : null;
    }
}
