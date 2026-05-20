package com.weiqiang.skyai.advisor;

import com.weiqiang.skyai.rag.online.model.RetrievalResult;
import com.weiqiang.skyai.rag.online.service.OnlineRetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagAdvisorTests {

    @Test
    void adviseCallShouldInjectRetrievalContextIntoPrompt() {
        OnlineRetrievalService onlineRetrievalService = mock(OnlineRetrievalService.class);
        RagAdvisor advisor = new RagAdvisor(onlineRetrievalService);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        AtomicReference<ChatClientRequest> capturedRequest = new AtomicReference<>();
        when(chain.nextCall(any(ChatClientRequest.class))).thenAnswer(invocation -> {
            capturedRequest.set(invocation.getArgument(0));
            return mock(ChatClientResponse.class);
        });
        when(onlineRetrievalService.retrieve("where is my order")).thenReturn(
                new RetrievalResult("where is my order", "chunk-a\nchunk-b", List.of())
        );

        ChatClientRequest request = new ChatClientRequest(new Prompt("where is my order"), new HashMap<>());

        advisor.adviseCall(request, chain);

        verify(onlineRetrievalService).retrieve("where is my order");
        assertEquals(2, capturedRequest.get().prompt().getInstructions().size());
        assertInstanceOf(SystemMessage.class, capturedRequest.get().prompt().getInstructions().get(0));
        SystemMessage systemMessage = (SystemMessage) capturedRequest.get().prompt().getInstructions().get(0);
        assertTrue(systemMessage.getText().contains("使用以下检索上下文回答问题："));
        assertTrue(systemMessage.getText().contains("chunk-a"));
        assertTrue(systemMessage.getText().contains("chunk-b"));
    }

    @Test
    void adviseStreamShouldSkipContextWhenRetrievalIsEmpty() {
        OnlineRetrievalService onlineRetrievalService = mock(OnlineRetrievalService.class);
        RagAdvisor advisor = new RagAdvisor(onlineRetrievalService);
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        AtomicReference<ChatClientRequest> capturedRequest = new AtomicReference<>();
        when(chain.nextStream(any(ChatClientRequest.class))).thenAnswer(invocation -> {
            capturedRequest.set(invocation.getArgument(0));
            return Flux.just(mock(ChatClientResponse.class));
        });
        when(onlineRetrievalService.retrieve("hello")).thenReturn(new RetrievalResult("hello", "", List.of()));

        ChatClientRequest request = new ChatClientRequest(new Prompt("hello"), new HashMap<>());

        advisor.adviseStream(request, chain).blockLast();

        verify(onlineRetrievalService).retrieve("hello");
        assertEquals(1, capturedRequest.get().prompt().getInstructions().size());
        assertTrue(!(capturedRequest.get().prompt().getInstructions().get(0) instanceof SystemMessage));
    }
}
