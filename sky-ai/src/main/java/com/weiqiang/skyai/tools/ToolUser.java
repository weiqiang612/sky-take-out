package com.weiqiang.skyai.tools;

import org.springframework.ai.chat.model.ToolContext;

final class ToolUser {

    private ToolUser() {
    }

    static String userId(ToolContext context) {
        Object value = context == null ? null : context.getContext().get("userId");
        return value instanceof String userId && !userId.isBlank() ? userId : "anonymous";
    }
}
