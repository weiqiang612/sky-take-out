package com.weiqiang.skyai.memory.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class ToolFilterAdvisor implements CallAdvisor {

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        Set<String> allowedTools = allowedTools(chatClientRequest);
        ChatOptions options = chatClientRequest.prompt().getOptions();
        ToolCallingChatOptions toolOptions = options instanceof ToolCallingChatOptions existing
                ? existing.copy()
                : new DefaultToolCallingChatOptions();
        toolOptions.setToolNames(allowedTools);
        Prompt prompt = new Prompt(chatClientRequest.prompt().getInstructions(), toolOptions);
        return callAdvisorChain.nextCall(chatClientRequest.mutate().prompt(prompt).build());
    }

    @Override
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
            Set<String> tools = new LinkedHashSet<>();
            set.forEach(item -> {
                if (item instanceof String tool) {
                    tools.add(tool);
                }
            });
            return tools;
        }
        return Set.of();
    }
}
