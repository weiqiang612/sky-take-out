package com.weiqiang.skyai.websocket;

import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionRequest;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import com.weiqiang.skyai.intent_recognition.service.CustomerIntentRecognitionService;
import com.weiqiang.skyai.advisor.IntentRecognitionAdvisor;
import com.weiqiang.skyai.advisor.SafeToolCallAdvisor;
import com.weiqiang.skyai.advisor.RagAdvisor;
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
    private final RagAdvisor ragAdvisor;
    private final ToolFilterAdvisor toolFilterAdvisor;
    private final SafeToolCallAdvisor safeToolCallAdvisor;
    private final MemoryWriterService memoryWriterService;
    private final CustomerIntentRecognitionService customerIntentRecognitionService;
    private final ChatHistoryService chatHistoryService;

    public AgentChatService(@Qualifier("toolChatClientBuilder") ChatClient.Builder chatClientBuilder,
                            IntentRecognitionAdvisor intentRecognitionAdvisor,
                            UserContextAdvisor userContextAdvisor,
                            MessageChatMemoryAdvisor messageChatMemoryAdvisor,
                            RagAdvisor ragAdvisor,
                            ToolFilterAdvisor toolFilterAdvisor,
                            SafeToolCallAdvisor safeToolCallAdvisor,
                            MemoryWriterService memoryWriterService,
                            CustomerIntentRecognitionService customerIntentRecognitionService,
                            ChatHistoryService chatHistoryService) {
        this.chatClientBuilder = chatClientBuilder;
        this.intentRecognitionAdvisor = intentRecognitionAdvisor;
        this.userContextAdvisor = userContextAdvisor;
        this.messageChatMemoryAdvisor = messageChatMemoryAdvisor;
        this.ragAdvisor = ragAdvisor;
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
        ChatClientResponse response = executeCall(question, conversationId, userId, preIntent, Map.of());
        IntentRecognitionResult intentResult = (IntentRecognitionResult) response.context().get("intentResult");
        memoryWriterService.writeTurn(userId, conversationId, intentResult != null ? intentResult : preIntent);
        return extractAnswer(response);
    }

    public String askStep(String question, String conversationId, String userId, IntentRecognitionResult stepIntent) {
        log.info("Executing step conversationId={} intent={} question={}", conversationId, stepIntent.intent().value(), question);
        ChatClientResponse response = executeCall(
                question,
                conversationId,
                userId,
                stepIntent,
                Map.of(
                        "currentStepIntent", stepIntent.intent().value(),
                        "skipProfileInjection", true
                )
        );
        String answer = extractAnswer(response);
        log.info("Step answer received conversationId={} answerLength={}", conversationId, answer.length());
        return answer;
    }

    public List<CallAdvisor> advisors(IntentRecognitionResult preIntent) {
        List<CallAdvisor> advisors = new ArrayList<>();
        advisors.add(intentRecognitionAdvisor);
        advisors.add(userContextAdvisor);
        advisors.add(messageChatMemoryAdvisor);
        if (shouldUseRag(preIntent)) {
            advisors.add(ragAdvisor);
        }
        advisors.add(toolFilterAdvisor);
        advisors.add(safeToolCallAdvisor);
        return advisors;
    }

    public String confirmationQuestion(IntentRecognitionResult intentResult) {
        if (intentResult == null) {
            return "请确认该操作！";
        }

        // 只有在明确需要人工确认的情况下，优先使用模型给的原因
        if (intentResult.requiresHumanConfirmation() && StringUtils.hasText(intentResult.humanConfirmationReason())) {
            return intentResult.humanConfirmationReason();
        }

        // 如果没有配置 reason，使用本地的 switch-case 模板硬性兜底弹窗文本
        return switch (intentResult.intent()) {
            case CANCEL_ORDER       -> buildQuestion("是否确认取消订单", intentResult);
            case REQUEST_REFUND     -> buildQuestion("是否确认申请退款", intentResult);
            case CHANGE_ADDRESS     -> buildQuestion("是否确认修改配送地址", intentResult);
            case REPORT_MISSING_ITEM -> buildQuestion("是否确认申报缺漏商品", intentResult);
            default                 -> "请确认该操作。";
        };
    }

    public String otherIntentResponse(IntentRecognitionResult intentResult) {
        if (intentResult != null && StringUtils.hasText(intentResult.clarificationQuestion())) {
            return intentResult.clarificationQuestion();
        }
        return DEFAULT_OTHER_CLARIFICATION;
    }

    public AgentChatConfirmationFrame confirmationFrame(IntentRecognitionResult intentResult) {
        String orderId = orderReferenceText(intentResult);
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

    public IntentRecognitionResult confirmedIntent(String intentValue,  IntentRecognitionResult original) {
        IntentType intent = IntentType.fromValue(intentValue);
        if (intent == null) {
            throw new IllegalArgumentException("intent is required");
        }
        return new IntentRecognitionResult(
                intent,
                ConfidenceLevel.HIGH,
                original != null && original.entities() != null
                        ? original.entities()   // ← 复用原始 entities
                        : Map.of(),
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
        return streamChat(question, conversationId, userId, preIntent, Map.of());
    }

    public Flux<ChatClientResponse> streamChat(String question,
                                               String conversationId,
                                               String userId,
                                               IntentRecognitionResult preIntent,
                                               Map<String, Object> extraContext) {
        log.info("Starting chat stream for conversationId: {}, userId: {}, question: {}, preIntent: {}", conversationId, userId, question, preIntent.intent().value());
        Map<String, Object> contextParams = new java.util.LinkedHashMap<>();
        contextParams.put(ChatMemory.CONVERSATION_ID, conversationId);
        contextParams.put("userId", userId);
        contextParams.put("preRecognizedIntent", preIntent);
        if (extraContext != null) {
            contextParams.putAll(extraContext);
        }
        Flux<ChatClientResponse> flux = chatClientBuilder.build().prompt()
                .advisors(advisors(preIntent).toArray(CallAdvisor[]::new))
                .advisors(advisor -> contextParams.forEach(advisor::param))
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

    private ChatClientResponse executeCall(String question,
                                           String conversationId,
                                           String userId,
                                           IntentRecognitionResult preIntent,
                                           Map<String, Object> extraContext) {
        Map<String, Object> contextParams = new java.util.LinkedHashMap<>();
        contextParams.put(ChatMemory.CONVERSATION_ID, conversationId);
        contextParams.put("userId", userId);
        contextParams.put("preRecognizedIntent", preIntent);
        if (extraContext != null) {
            contextParams.putAll(extraContext);
        }
        return chatClientBuilder.build().prompt()
                .advisors(advisors(preIntent).toArray(CallAdvisor[]::new))
                .advisors(advisor -> contextParams.forEach(advisor::param))
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
        String orderId = orderReferenceText(intentResult);
        if (StringUtils.hasText(orderId)) {
            return prefix + " " + orderId + "?";
        }
        return prefix + "?";
    }

    private String orderReferenceText(IntentRecognitionResult intentResult) {
        if (intentResult == null || intentResult.entities() == null || intentResult.entities().isEmpty()) {
            return null;
        }

        java.util.LinkedHashSet<String> orderRefs = new java.util.LinkedHashSet<>();
        String orderIds = intentResult.entities().get("order_ids");
        if (StringUtils.hasText(orderIds)) {
            for (String part : orderIds.split("[,，;；\\s]+")) {
                if (StringUtils.hasText(part)) {
                    orderRefs.add(part.trim());
                }
            }
        }

        String orderId = intentResult.entities().get("order_id");
        if (StringUtils.hasText(orderId)) {
            orderRefs.add(orderId.trim());
        }

        for (Map.Entry<String, String> entry : intentResult.entities().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key != null && key.matches("order_id_\\d+") && StringUtils.hasText(value)) {
                orderRefs.add(value.trim());
            }
        }

        if (orderRefs.isEmpty()) {
            return null;
        }
        return String.join("、", orderRefs);
    }
}
