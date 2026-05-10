package com.weiqiang.skyai.controller;

import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.memory.advisor.IntentRecognitionAdvisor;
import com.weiqiang.skyai.memory.advisor.ToolFilterAdvisor;
import com.weiqiang.skyai.memory.advisor.UserContextAdvisor;
import com.weiqiang.skyai.memory.service.MemoryWriterService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class ChatController {

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    // 意图识别顾问，负责识别用户输入的意图
    private final IntentRecognitionAdvisor intentRecognitionAdvisor;
    // 用户上下文顾问，负责在对话中维护用户的上下文信息
    private final UserContextAdvisor userContextAdvisor;
    // 消息记忆顾问，负责将对话内容存储到记忆中
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final QuestionAnswerAdvisor questionAnswerAdvisor;
    // 工具过滤顾问，负责根据用户的意图和上下文信息过滤可用的工具，确保模型调用工具时的安全性和相关性
    private final ToolFilterAdvisor toolFilterAdvisor;
    private final MemoryWriterService memoryWriterService;

    @GetMapping("/ask")
    public Map<String, String> ask(@RequestParam("question") String question,
                                   @RequestParam(value = "conversationId", defaultValue = ChatMemory.DEFAULT_CONVERSATION_ID) String conversationId,
                                   @RequestParam(value = "userId", defaultValue = "anonymous") String userId) {
        ChatClientResponse response = chatClientBuilderProvider.getObject().build().prompt()
                // 先进行意图识别，之后补用户画像，接着关联记忆，最后补RAG，实际执行顺序由advisor的order决定
                .advisors(intentRecognitionAdvisor, userContextAdvisor, messageChatMemoryAdvisor, questionAnswerAdvisor, toolFilterAdvisor)
                // 为每个 advisor 传入对话ID和用户ID参数，方便它们在处理时使用这些信息
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId).param("userId", userId))
                // 工具调用时也传入用户ID，方便工具在执行时获取用户相关信息
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
