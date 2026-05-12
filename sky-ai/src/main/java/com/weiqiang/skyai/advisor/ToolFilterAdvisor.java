package com.weiqiang.skyai.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Set;

@Component
public class ToolFilterAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ToolFilterAdvisor.class);

    private final DynamicToolCallbackRegistry toolCallbackRegistry;

    public ToolFilterAdvisor(@NonNull DynamicToolCallbackRegistry toolCallbackRegistry) {
        this.toolCallbackRegistry = toolCallbackRegistry;
    }

    @Override
    @NonNull
    public ChatClientResponse adviseCall(@NonNull ChatClientRequest chatClientRequest, @NonNull CallAdvisorChain callAdvisorChain) {
        log.info("ToolFilterAdvisor start advising for request.context().keyset(): {}", chatClientRequest.context().keySet());
        return applyToolFilter(chatClientRequest, prompt -> callAdvisorChain.nextCall(chatClientRequest.mutate().prompt(prompt).build()));
    }

    @Override
    @NonNull
    public reactor.core.publisher.Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest chatClientRequest, @NonNull StreamAdvisorChain streamAdvisorChain) {
        log.info("ToolFilterAdvisor start advising for request.context().keyset(): {}", chatClientRequest.context().keySet());
        return applyToolFilter(chatClientRequest, prompt -> streamAdvisorChain.nextStream(chatClientRequest.mutate().prompt(prompt).build()));
    }

    private <T> T applyToolFilter(ChatClientRequest chatClientRequest, java.util.function.Function<Prompt, T> next) {
        Set<String> allowedTools = allowedTools(chatClientRequest);
        List<org.springframework.ai.tool.ToolCallback> selectedToolCallbacks = toolCallbackRegistry.selectCallbacks(allowedTools);
        log.info("ToolFilterAdvisor final tools count={}, tools={}{}",
                allowedTools.size(),
                previewTools(allowedTools),
                allowedTools.size() > 30 ? " ..." : "");
        ChatOptions options = chatClientRequest.prompt().getOptions();
        ToolCallingChatOptions toolOptions = options instanceof ToolCallingChatOptions existing
                ? existing.copy()
                : new DefaultToolCallingChatOptions();
        toolOptions.setToolNames(allowedTools);
        toolOptions.setToolCallbacks(selectedToolCallbacks);
        Prompt prompt = new Prompt(chatClientRequest.prompt().getInstructions(), toolOptions);
        return next.apply(prompt);
    }

    @Override
    @NonNull
    public String getName() {
        return "toolFilterAdvisor";
    }

    // 确保这个顾问在意图识别顾问和用户上下文顾问之后执行，以便它可以根据识别的意图和用户上下文来过滤工具
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 4;
    }

    private Set<String> allowedTools(ChatClientRequest request) {
        Object value = request.context().get("allowedTools");
        if (value instanceof Set<?> set) {
            return set.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        }
        return Set.of();
    }

    private String previewTools(Set<String> tools) {
        return tools.stream().limit(30).collect(java.util.stream.Collectors.joining(", "));
    }
}
