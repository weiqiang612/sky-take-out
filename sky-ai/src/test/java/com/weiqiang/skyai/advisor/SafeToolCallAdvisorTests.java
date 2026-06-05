package com.weiqiang.skyai.advisor;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SafeToolCallAdvisorTests {

    @Test
    void stopsOnDuplicateToolCallSignature() {
        SafeToolCallAdvisor advisor = new SafeToolCallAdvisor(mock(ToolCallingManager.class), 4);
        ChatClientRequest request = new ChatClientRequest(new Prompt("hi"), new HashMap<>());
        advisor.doInitializeLoop(request, mock(CallAdvisorChain.class));

        ChatClientResponse response = responseWithToolCall(request.context(), "searchDishes", "{\"keyword\":\"rice\"}");
        ChatClientResponse first = advisor.doAfterCall(response, mock(CallAdvisorChain.class));
        ChatClientResponse second = advisor.doAfterCall(response, mock(CallAdvisorChain.class));

        assertTrue(first.chatResponse().hasToolCalls());
        assertFalse(second.chatResponse().hasToolCalls());
        assertTrue(second.chatResponse().getResult().getOutput().getText().contains("抱歉，我目前无法自动处理该请求"));
    }

    @Test
    void stopsAfterFourToolCallRounds() {
        SafeToolCallAdvisor advisor = new SafeToolCallAdvisor(mock(ToolCallingManager.class), 4);
        ChatClientRequest request = new ChatClientRequest(new Prompt("hi"), new HashMap<>());
        advisor.doInitializeLoopStream(request, mock(StreamAdvisorChain.class));

        ChatClientResponse fourth = null;
        for (int i = 0; i < 4; i++) {
            ChatClientResponse response = responseWithToolCall(request.context(), "searchDishes", "{\"keyword\":\"dish" + i + "\"}");
            fourth = advisor.doAfterStream(response, mock(StreamAdvisorChain.class));
        }
        ChatClientResponse fifth = advisor.doAfterStream(responseWithToolCall(request.context(), "searchDishes",
                "{\"keyword\":\"dish5\"}"), mock(StreamAdvisorChain.class));

        assertTrue(fourth.chatResponse().hasToolCalls());
        assertFalse(fifth.chatResponse().hasToolCalls());
    }

    @Test
    void stopsAfterCustomToolCallRounds() {
        SafeToolCallAdvisor advisor = new SafeToolCallAdvisor(mock(ToolCallingManager.class), 2);
        ChatClientRequest request = new ChatClientRequest(new Prompt("hi"), new HashMap<>());
        advisor.doInitializeLoopStream(request, mock(StreamAdvisorChain.class));

        ChatClientResponse second = null;
        for (int i = 0; i < 2; i++) {
            ChatClientResponse response = responseWithToolCall(request.context(), "searchDishes", "{\"keyword\":\"dish" + i + "\"}");
            second = advisor.doAfterStream(response, mock(StreamAdvisorChain.class));
        }
        ChatClientResponse third = advisor.doAfterStream(responseWithToolCall(request.context(), "searchDishes",
                "{\"keyword\":\"dish3\"}"), mock(StreamAdvisorChain.class));

        assertTrue(second.chatResponse().hasToolCalls());
        assertFalse(third.chatResponse().hasToolCalls());
    }

    private ChatClientResponse responseWithToolCall(Map<String, Object> context, String name, String arguments) {
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("tool-1", "function", name, arguments)))
                .build();
        ChatResponse chatResponse = ChatResponse.builder()
                .generations(List.of(new Generation(assistantMessage)))
                .build();
        return ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(context)
                .build();
    }
}
