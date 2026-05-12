package com.weiqiang.skyai.memory.advisor;

import com.weiqiang.skyai.advisor.DynamicToolCallbackRegistry;
import com.weiqiang.skyai.advisor.ToolFilterAdvisor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolFilterAdvisorTests {

    @Test
    void adviseStreamInjectsAllowedToolsIntoPromptOptions() {
        DynamicToolCallbackRegistry registry = mock(DynamicToolCallbackRegistry.class);
        ToolCallback callback = mock(ToolCallback.class);
        when(registry.selectCallbacks(Set.of("cancelOrder", "requestRefund"))).thenReturn(List.of(callback));

        ToolFilterAdvisor advisor = new ToolFilterAdvisor(registry);
        AtomicReference<ChatClientRequest> capturedRequest = new AtomicReference<>();
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(org.mockito.ArgumentMatchers.any(ChatClientRequest.class))).thenAnswer(invocation -> {
            capturedRequest.set(invocation.getArgument(0));
            return Flux.just(mock(ChatClientResponse.class));
        });

        ChatClientRequest request = new ChatClientRequest(
                new Prompt("please cancel it", new DefaultToolCallingChatOptions()),
                new HashMap<>(Map.of("allowedTools", Set.of("cancelOrder", "requestRefund")))
        );

        advisor.adviseStream(request, chain).blockLast();

        ToolCallingChatOptions options = (ToolCallingChatOptions) capturedRequest.get().prompt().getOptions();
        assertEquals(Set.of("cancelOrder", "requestRefund"), options.getToolNames());
        assertEquals(1, options.getToolCallbacks().size());
        assertTrue(options.getToolCallbacks().contains(callback));
    }
}
