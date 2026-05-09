package com.weiqiang.skyai.intent_recognition.client;

import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionRequest;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ChatClientCustomerIntentRecognitionClient implements CustomerIntentRecognitionClient {

    private static final String SYSTEM_PROMPT = """
            你是外卖客服意图识别器。
            任务：根据用户消息和对话历史，识别一个最合适的客服意图，并提取相关实体。
            规则：
            1. 不确定时不要猜，优先返回 other 或最保守意图。
            2. 有歧义时把候选意图写入 possible_intents，不要强行只选一个。
            3. confidence 为 low 时必须给出 clarification_question。
            4. cancel_order、request_refund、change_address 这类高风险操作必须标记 requires_human_confirmation=true，并说明原因。
            5. entities 只返回字符串键值对，优先提取 order_id、item_name、address 等业务实体。
            6. 只返回可被 JSON 反序列化的结构化结果，不要输出解释性文本。

            可用意图：
            - order_status
            - cancel_order
            - request_refund
            - track_delivery
            - report_missing_item
            - change_address
            - faq
            - escalate_to_human
            - other
            """;

    private final ChatClient.Builder gptChatClient;

    @Override
    public IntentRecognitionResult recognize(IntentRecognitionRequest request) {
        String userText = buildUserText(request);
        return gptChatClient.build()
                .prompt()
                .system(s -> s.text(SYSTEM_PROMPT))
                .user(userText)
                .call()
                .entity(IntentRecognitionResult.class);
    }

    private String buildUserText(IntentRecognitionRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("message:\n");
        builder.append(StringUtils.hasText(request.message()) ? request.message().trim() : "");
        builder.append("\n\nconversation_history:\n");

        List<String> history = request.conversationHistory();
        if (history != null && !history.isEmpty()) {
            for (int i = 0; i < history.size(); i++) {
                String item = history.get(i);
                if (!StringUtils.hasText(item)) {
                    continue;
                }
                builder.append(i + 1).append(". ").append(item.trim()).append('\n');
            }
        }
        return builder.toString();
    }
}
