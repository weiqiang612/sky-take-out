package com.weiqiang.skyai.intent_recognition.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatClientCustomerIntentRecognitionClientTests {

    @Test
    void repairJsonContentRemovesBareStringPollution() {
        String malformed = """
                {
                  "intent": "other",
                  "other",
                  "possible_intents": ["other"],
                  "confidence": "LOW",
                  "entities": {},
                  "requires_human_confirmation": false,
                  "human_confirmation_reason": "",
                  "clarification_question": "您好，请问您有什么关于外卖订单的问题需要帮助吗？"
                }
                """;

        String repaired = ChatClientCustomerIntentRecognitionClient.repairJsonContent(malformed);

        assertTrue(repaired.contains("\"intent\": \"other\""));
        assertTrue(repaired.contains("\"possible_intents\": [\"other\"]"));
        assertFalse(repaired.lines().anyMatch(line -> line.trim().equals("\"other\",")));
        assertTrue(repaired.startsWith("{"));
        assertTrue(repaired.endsWith("}"));
    }

    @Test
    void extractJsonReturnsWholeObjectWhenWrappedInText() {
        String wrapped = "前言文字\n{\"intent\":\"other\"}\n后记";

        assertEquals("{\"intent\":\"other\"}", ChatClientCustomerIntentRecognitionClient.extractJson(wrapped));
    }
}
