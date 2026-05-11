package com.weiqiang.skyai.controller;

import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.memory.advisor.IntentRecognitionAdvisor;
import com.weiqiang.skyai.memory.advisor.ToolFilterAdvisor;
import com.weiqiang.skyai.memory.advisor.UserContextAdvisor;
import com.weiqiang.skyai.memory.service.MemoryWriterService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/ai")
public class ChatController {

    private final ChatClient.Builder chatClientBuilder;
    private final IntentRecognitionAdvisor intentRecognitionAdvisor;
    private final UserContextAdvisor userContextAdvisor;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final QuestionAnswerAdvisor questionAnswerAdvisor;
    private final ToolFilterAdvisor toolFilterAdvisor;
    private final MemoryWriterService memoryWriterService;

    public ChatController(@Qualifier("toolChatClientBuilder") ChatClient.Builder chatClientBuilder,
                          IntentRecognitionAdvisor intentRecognitionAdvisor,
                          UserContextAdvisor userContextAdvisor,
                          MessageChatMemoryAdvisor messageChatMemoryAdvisor,
                          QuestionAnswerAdvisor questionAnswerAdvisor,
                          ToolFilterAdvisor toolFilterAdvisor,
                          MemoryWriterService memoryWriterService) {
        this.chatClientBuilder = chatClientBuilder;
        this.intentRecognitionAdvisor = intentRecognitionAdvisor;
        this.userContextAdvisor = userContextAdvisor;
        this.messageChatMemoryAdvisor = messageChatMemoryAdvisor;
        this.questionAnswerAdvisor = questionAnswerAdvisor;
        this.toolFilterAdvisor = toolFilterAdvisor;
        this.memoryWriterService = memoryWriterService;
    }

    @GetMapping("/ask")
    public Map<String, String> ask(@RequestParam("question") String question,
                                   @RequestParam(value = "conversationId", defaultValue = ChatMemory.DEFAULT_CONVERSATION_ID) String conversationId,
                                   @RequestParam(value = "userId", defaultValue = "anonymous") String userId) {
        ChatClientResponse response = chatClientBuilder.build().prompt()
                .advisors(intentRecognitionAdvisor, userContextAdvisor, messageChatMemoryAdvisor, questionAnswerAdvisor, toolFilterAdvisor)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId).param("userId", userId))
                .toolContext(Map.of("userId", userId))
                .user(question)
                .call()
                .chatClientResponse();
        IntentRecognitionResult intentResult = (IntentRecognitionResult) response.context().get("intentResult");
        memoryWriterService.writeTurn(userId, conversationId, intentResult);
        return Map.of(
                "question", question,
                "answer", response.chatResponse().getResult().getOutput().getText()
        );
    }
}
