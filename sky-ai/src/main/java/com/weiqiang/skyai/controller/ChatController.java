package com.weiqiang.skyai.controller;

import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.websocket.AgentChatService;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/ai")
public class ChatController {

    private final AgentChatService agentChatService;

    public ChatController(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    @GetMapping("/ask")
    public Map<String, String> ask(@RequestParam("question") String question,
                                   @RequestParam(value = "conversationId", defaultValue = ChatMemory.DEFAULT_CONVERSATION_ID) String conversationId,
                                   @RequestParam(value = "userId", defaultValue = "anonymous") String userId) {
        IntentRecognitionResult preIntent = agentChatService.recognizeIntent(question, conversationId, userId);

        if (preIntent.intent() == IntentType.OTHER) {
            return Map.of("question", question, "answer", agentChatService.otherIntentResponse(preIntent));
        }

        if (preIntent.requiresHumanConfirmation()) {
            return Map.of("question", question, "answer", agentChatService.confirmationQuestion(preIntent));
        }

        return Map.of("question", question, "answer", agentChatService.ask(question, conversationId, userId, preIntent));
    }

    private boolean shouldUseRag(IntentRecognitionResult intentResult) {
        return intentResult != null && intentResult.intent() == IntentType.FAQ;
    }
}
