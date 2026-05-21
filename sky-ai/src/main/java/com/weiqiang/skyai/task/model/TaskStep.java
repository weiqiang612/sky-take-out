package com.weiqiang.skyai.task.model;

import com.weiqiang.skyai.intent_recognition.model.IntentType;

import java.util.Map;

public record TaskStep(
        int stepNumber,
        IntentType intent,
        Map<String, String> entities,
        boolean requiresConfirmation,
        String instruction
) {
}
