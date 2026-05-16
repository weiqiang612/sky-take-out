package com.weiqiang.skyai.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SafeToolCallAdvisor extends ToolCallAdvisor {

    private static final String STATE_KEY = SafeToolCallAdvisor.class.getName() + ".state";
    private static final int MAX_TOOL_CALL_ROUNDS = 4;
    private static final String STOP_MESSAGE = "已查询到的信息不足以继续自动处理，请你确认一下需要的菜品或改用更明确的说法。";

    public SafeToolCallAdvisor(ToolCallingManager toolCallingManager) {
        super(toolCallingManager, Ordered.LOWEST_PRECEDENCE - 100, true, false);
    }

    @Override
    protected ChatClientRequest doInitializeLoop(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        state(chatClientRequest.context());
        return chatClientRequest;
    }

    @Override
    protected ChatClientRequest doInitializeLoopStream(ChatClientRequest chatClientRequest,
                                                       StreamAdvisorChain streamAdvisorChain) {
        state(chatClientRequest.context());
        return chatClientRequest;
    }

    @Override
    protected ChatClientResponse doAfterCall(ChatClientResponse chatClientResponse, CallAdvisorChain callAdvisorChain) {
        return guard(chatClientResponse);
    }

    @Override
    protected ChatClientResponse doAfterStream(ChatClientResponse chatClientResponse, StreamAdvisorChain streamAdvisorChain) {
        return guard(chatClientResponse);
    }

    private ChatClientResponse guard(ChatClientResponse chatClientResponse) {
        if (chatClientResponse == null || chatClientResponse.chatResponse() == null
                || chatClientResponse.chatResponse().getResult() == null
                || chatClientResponse.chatResponse().getResult().getOutput() == null) {
            return chatClientResponse;
        }
        AssistantMessage assistantMessage = chatClientResponse.chatResponse().getResult().getOutput();
        if (!assistantMessage.hasToolCalls()) {
            return chatClientResponse;
        }
        LoopState loopState = state(chatClientResponse.context());
        Set<String> signatures = new HashSet<>();
        for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
            String signature = signature(toolCall);
            if (!signatures.add(signature) || loopState.seenToolCalls.contains(signature)) {
                return stop(chatClientResponse);
            }
        }
        if (loopState.toolCallRounds >= MAX_TOOL_CALL_ROUNDS) {
            return stop(chatClientResponse);
        }
        loopState.toolCallRounds++;
        loopState.seenToolCalls.addAll(signatures);
        return chatClientResponse;
    }

    private ChatClientResponse stop(ChatClientResponse chatClientResponse) {
        ChatResponse fallback = ChatResponse.builder()
                .from(chatClientResponse.chatResponse())
                .generations(List.of(new Generation(AssistantMessage.builder().content(STOP_MESSAGE).build())))
                .build();
        return chatClientResponse.mutate().chatResponse(fallback).build();
    }

    private LoopState state(Map<String, Object> context) {
        return (LoopState) context.computeIfAbsent(STATE_KEY, key -> new LoopState());
    }

    private String signature(AssistantMessage.ToolCall toolCall) {
        String arguments = toolCall.arguments() == null ? "" : toolCall.arguments().trim();
        return toolCall.name() + "\u0000" + arguments;
    }

    private static final class LoopState {
        private int toolCallRounds;
        private final Set<String> seenToolCalls = new HashSet<>();
    }
}
