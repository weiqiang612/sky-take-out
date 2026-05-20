package com.weiqiang.skyai.websocket;

import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionRequest;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.intent_recognition.service.CustomerIntentRecognitionService;
import com.weiqiang.skyai.advisor.IntentRecognitionAdvisor;
import com.weiqiang.skyai.advisor.SafeToolCallAdvisor;
import com.weiqiang.skyai.advisor.ToolFilterAdvisor;
import com.weiqiang.skyai.advisor.UserContextAdvisor;
import com.weiqiang.skyai.memory.service.ChatHistoryService;
import com.weiqiang.skyai.memory.service.MemoryWriterService;
import com.weiqiang.skyai.websocket.model.AgentChatConfirmationFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class AgentChatService {

    private static final String DEFAULT_OTHER_CLARIFICATION = "抱歉，我暂时没法确定你的具体需求。可以补充一下订单号、商品或想处理的事项吗？";

    private final ChatClient.Builder chatClientBuilder;
    private final IntentRecognitionAdvisor intentRecognitionAdvisor;
    private final UserContextAdvisor userContextAdvisor;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final QuestionAnswerAdvisor questionAnswerAdvisor;
    private final ToolFilterAdvisor toolFilterAdvisor;
    private final SafeToolCallAdvisor safeToolCallAdvisor;
    private final MemoryWriterService memoryWriterService;
    private final CustomerIntentRecognitionService customerIntentRecognitionService;
    private final ChatHistoryService chatHistoryService;

    public AgentChatService(@Qualifier("toolChatClientBuilder") ChatClient.Builder chatClientBuilder,
                            IntentRecognitionAdvisor intentRecognitionAdvisor,
                            UserContextAdvisor userContextAdvisor,
                            MessageChatMemoryAdvisor messageChatMemoryAdvisor,
                            QuestionAnswerAdvisor questionAnswerAdvisor,
                            ToolFilterAdvisor toolFilterAdvisor,
                            SafeToolCallAdvisor safeToolCallAdvisor,
                            MemoryWriterService memoryWriterService,
                            CustomerIntentRecognitionService customerIntentRecognitionService,
                            ChatHistoryService chatHistoryService) {
        this.chatClientBuilder = chatClientBuilder;
        this.intentRecognitionAdvisor = intentRecognitionAdvisor;
        this.userContextAdvisor = userContextAdvisor;
        this.messageChatMemoryAdvisor = messageChatMemoryAdvisor;
        this.questionAnswerAdvisor = questionAnswerAdvisor;
        this.toolFilterAdvisor = toolFilterAdvisor;
        this.safeToolCallAdvisor = safeToolCallAdvisor;
        this.memoryWriterService = memoryWriterService;
        this.customerIntentRecognitionService = customerIntentRecognitionService;
        this.chatHistoryService = chatHistoryService;
    }

    public IntentRecognitionResult recognizeIntent(String question, String conversationId, String userId) {
        return customerIntentRecognitionService.recognize(
                new IntentRecognitionRequest(question, chatHistoryService.buildHistory(conversationId, userId))
        );
    }

    public String ask(String question, String conversationId, String userId, IntentRecognitionResult preIntent) {
        ChatClientResponse response = executeCall(question, conversationId, userId, preIntent);
        IntentRecognitionResult intentResult = (IntentRecognitionResult) response.context().get("intentResult");
        memoryWriterService.writeTurn(userId, conversationId, intentResult != null ? intentResult : preIntent);
        return extractAnswer(response);
    }

    public List<CallAdvisor> advisors(IntentRecognitionResult preIntent) {
        List<CallAdvisor> advisors = new ArrayList<>();
        advisors.add(intentRecognitionAdvisor);
        advisors.add(userContextAdvisor);
        advisors.add(messageChatMemoryAdvisor);
        if (shouldUseRag(preIntent)) {
            advisors.add(questionAnswerAdvisor);
        }
        advisors.add(toolFilterAdvisor);
        advisors.add(safeToolCallAdvisor);
        return advisors;
    }

    public String confirmationQuestion(IntentRecognitionResult intentResult) {
        if (intentResult == null) {
            return "Please confirm this action.";
        }
        if (StringUtils.hasText(intentResult.clarificationQuestion())) {
            return intentResult.clarificationQuestion();
        }
        return switch (intentResult.intent()) {
            case CANCEL_ORDER -> buildQuestion("Do you want to cancel order", intentResult);
            case REQUEST_REFUND -> buildQuestion("Do you want to request a refund for order", intentResult);
            case CHANGE_ADDRESS -> buildQuestion("Do you want to change the delivery address for order", intentResult);
            default -> "Please confirm this action.";
        };
    }

    public String otherIntentResponse(IntentRecognitionResult intentResult) {
        if (intentResult != null && StringUtils.hasText(intentResult.clarificationQuestion())) {
            return intentResult.clarificationQuestion();
        }
        return DEFAULT_OTHER_CLARIFICATION;
    }

    public AgentChatConfirmationFrame confirmationFrame(IntentRecognitionResult intentResult) {
        String orderId = intentResult.entities() == null ? null : intentResult.entities().get("order_id");
        return new AgentChatConfirmationFrame(
                "confirmation",
                intentResult.intent().value(),
                StringUtils.hasText(orderId) ? orderId : null,
                confirmationQuestion(intentResult),
                StringUtils.hasText(intentResult.humanConfirmationReason())
                        ? intentResult.humanConfirmationReason()
                        : "This action requires human confirmation."
        );
    }

    public IntentRecognitionResult confirmedIntent(String intentValue) {
        IntentType intent = IntentType.fromValue(intentValue);
        if (intent == null) {
            throw new IllegalArgumentException("intent is required");
        }
        return new IntentRecognitionResult(
                intent,
                ConfidenceLevel.HIGH,
                Map.of(),
                List.of(intent),
                null,
                false,
                null
        );
    }

    public void writeTurn(String userId, String conversationId, IntentRecognitionResult intentResult) {
        memoryWriterService.writeTurn(userId, conversationId, intentResult);
    }

    public Flux<ChatClientResponse> streamChat(String question, String conversationId, String userId, IntentRecognitionResult preIntent) {
        log.info("Starting chat stream for conversationId: {}, userId: {}, question: {}, preIntent: {}", conversationId, userId, question, preIntent.intent().value());
        Flux<ChatClientResponse> flux = chatClientBuilder.build().prompt()
                .advisors(advisors(preIntent).toArray(CallAdvisor[]::new))
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId)
                        .param("userId", userId)
                        .param("preRecognizedIntent", preIntent))
                // 将用户ID作为上下文传给工具调用，以便工具调用时可以获取到用户相关信息
                .toolContext(Map.of("userId", userId))
                .user(question)
                .stream()
                .chatClientResponse();
        return applyStreamTimeout(flux);
    }

    Flux<ChatClientResponse> applyStreamTimeout(Flux<ChatClientResponse> flux) {
        return applyStreamTimeout(flux, null);
    }

    Flux<ChatClientResponse> applyStreamTimeout(Flux<ChatClientResponse> flux, Scheduler scheduler) {
        Flux<ChatClientResponse> timedFlux = scheduler == null
                ? flux.timeout(Duration.ofSeconds(30))
                : flux.timeout(Duration.ofSeconds(30), scheduler);
        return timedFlux.onErrorMap(TimeoutException.class, ex ->
                new IllegalStateException("Agent stream timed out after 30s", ex));
    }

    private ChatClientResponse executeCall(String question, String conversationId, String userId, IntentRecognitionResult preIntent) {
        return chatClientBuilder.build().prompt()
                .advisors(advisors(preIntent).toArray(CallAdvisor[]::new))
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId)
                        .param("userId", userId)
                        .param("preRecognizedIntent", preIntent))
                .toolContext(Map.of("userId", userId))
                .user(question)
                .call()
                .chatClientResponse();
    }

    private String extractAnswer(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null || response.chatResponse().getResult() == null
                || response.chatResponse().getResult().getOutput() == null) {
            return "";
        }
        String text = response.chatResponse().getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private boolean shouldUseRag(IntentRecognitionResult intentResult) {
        if (intentResult == null) return false;
        IntentType intent = intentResult.intent();
        return intent.isKnowledge()
                || intent == IntentType.CANCEL_ORDER
                || intent == IntentType.REQUEST_REFUND
                || intent == IntentType.REPORT_MISSING_ITEM;
    }

    private String buildQuestion(String prefix, IntentRecognitionResult intentResult) {
        String orderId = intentResult.entities() == null ? null : intentResult.entities().get("order_id");
        if (StringUtils.hasText(orderId)) {
            return prefix + " " + orderId + "?";
        }
        return prefix + "?";
    }
}
