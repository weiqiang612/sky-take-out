package com.weiqiang.skyai.controller;

import com.weiqiang.skyai.intent_recognition.client.CustomerIntentRecognitionClient;
import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionRequest;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
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

    @Qualifier("toolChatClientBuilder")
    private final ChatClient.Builder chatClientBuilder;
    private final IntentRecognitionAdvisor intentRecognitionAdvisor;
    private final UserContextAdvisor userContextAdvisor;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final QuestionAnswerAdvisor questionAnswerAdvisor;
    private final ToolFilterAdvisor toolFilterAdvisor;
    private final MemoryWriterService memoryWriterService;
    private final CustomerIntentRecognitionClient customerIntentRecognitionClient;
    private final com.weiqiang.skyai.memory.service.ChatHistoryService chatHistoryService;

    public ChatController(@Qualifier("toolChatClientBuilder") ChatClient.Builder chatClientBuilder,
                          IntentRecognitionAdvisor intentRecognitionAdvisor,
                          UserContextAdvisor userContextAdvisor,
                          MessageChatMemoryAdvisor messageChatMemoryAdvisor,
                          QuestionAnswerAdvisor questionAnswerAdvisor,
                          ToolFilterAdvisor toolFilterAdvisor,
                          MemoryWriterService memoryWriterService,
                          CustomerIntentRecognitionClient customerIntentRecognitionClient,
                          com.weiqiang.skyai.memory.service.ChatHistoryService chatHistoryService) {
        this.chatClientBuilder = chatClientBuilder;
        this.intentRecognitionAdvisor = intentRecognitionAdvisor;
        this.userContextAdvisor = userContextAdvisor;
        this.messageChatMemoryAdvisor = messageChatMemoryAdvisor;
        this.questionAnswerAdvisor = questionAnswerAdvisor;
        this.toolFilterAdvisor = toolFilterAdvisor;
        this.memoryWriterService = memoryWriterService;
        this.customerIntentRecognitionClient = customerIntentRecognitionClient;
        this.chatHistoryService = chatHistoryService;
    }

    @GetMapping("/ask")
    public Map<String, String> ask(@RequestParam("question") String question,
                                   @RequestParam(value = "conversationId", defaultValue = ChatMemory.DEFAULT_CONVERSATION_ID) String conversationId,
                                   @RequestParam(value = "userId", defaultValue = "anonymous") String userId) {

        // 前置一次意图识别（带历史），用于短路澄清场景
        IntentRecognitionResult preIntent = customerIntentRecognitionClient.recognize(
                new IntentRecognitionRequest(question, chatHistoryService.buildHistory(conversationId, userId))
        );

        if (preIntent != null && preIntent.intent() == IntentType.OTHER && preIntent.confidence() == ConfidenceLevel.LOW) {
            String clarificationQuestion = preIntent.clarificationQuestion() != null
                    ? preIntent.clarificationQuestion()
                    : "我没有完全理解您的问题。能详细说一下吗？";
            memoryWriterService.writeTurn(userId, conversationId, preIntent);
            return Map.of("question", question, "answer", clarificationQuestion, "type", "clarification");
        }

        // 继续处理，这次 advisor 中检测到已有意图结果可以跳过识别
        ChatClientResponse response = chatClientBuilder.build().prompt()
                .advisors(intentRecognitionAdvisor, userContextAdvisor, messageChatMemoryAdvisor, questionAnswerAdvisor, toolFilterAdvisor)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId).param("userId", userId).param("preRecognizedIntent", preIntent))
                .toolContext(Map.of("userId", userId))
                .user(question)
                .call()
                .chatClientResponse();
        IntentRecognitionResult intentResult = (IntentRecognitionResult) response.context().get("intentResult");
        memoryWriterService.writeTurn(userId, conversationId, intentResult != null ? intentResult : preIntent);
        return Map.of(
                "question", question,
                "answer", response.chatResponse().getResult().getOutput().getText()
        );
    }
}
