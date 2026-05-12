package com.weiqiang.skyai.advisor;

import com.weiqiang.skyai.tools.AddressTools;
import com.weiqiang.skyai.tools.CartTools;
import com.weiqiang.skyai.tools.MenuTools;
import com.weiqiang.skyai.tools.OrderTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class DynamicToolCallbackRegistry {

    private final Map<String, ToolCallback> toolCallbacksByName;

    public DynamicToolCallbackRegistry(OrderTools orderTools,
                                       MenuTools menuTools,
                                       CartTools cartTools,
                                       AddressTools addressTools) {
        Map<String, ToolCallback> callbacks = new LinkedHashMap<>();
        registerToolBean(callbacks, orderTools);
        registerToolBean(callbacks, menuTools);
        registerToolBean(callbacks, cartTools);
        registerToolBean(callbacks, addressTools);
        this.toolCallbacksByName = Map.copyOf(callbacks);
        log.info("Initialized dynamic tool registry with {} callbacks", this.toolCallbacksByName.size());
    }

    public List<ToolCallback> selectCallbacks(Set<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        List<ToolCallback> selected = new ArrayList<>();
        for (String toolName : toolNames) {
            ToolCallback callback = toolCallbacksByName.get(toolName);
            if (callback != null) {
                selected.add(callback);
            }
        }
        return selected;
    }

    public int size() {
        return toolCallbacksByName.size();
    }

    public Set<String> availableToolNames() {
        return toolCallbacksByName.keySet();
    }

    private void registerToolBean(Map<String, ToolCallback> callbacks, Object bean) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        ReflectionUtils.doWithMethods(targetClass, method -> registerToolMethod(callbacks, bean, method), this::isToolMethod);
    }

    private void registerToolMethod(Map<String, ToolCallback> callbacks, Object bean, Method method) {
        Tool tool = AnnotationUtils.findAnnotation(method, Tool.class);
        if (tool == null) {
            return;
        }
        String toolName = method.getName();
        if (callbacks.containsKey(toolName)) {
            throw new IllegalStateException("Duplicate tool name detected: " + toolName);
        }
        ToolDefinition definition = ToolDefinitions.from(method);
        ToolCallback callback = MethodToolCallback.builder()
                .toolDefinition(definition)
                .toolMethod(method)
                .toolObject(bean)
                .build();
        callbacks.put(toolName, callback);
    }

    private boolean isToolMethod(Method method) {
        return AnnotationUtils.findAnnotation(method, Tool.class) != null && !method.isSynthetic();
    }
}


