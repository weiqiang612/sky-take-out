package com.weiqiang.skyai.intent_recognition.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiqiang.skyai.intent_recognition.model.ConfidenceLevel;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionRequest;
import com.weiqiang.skyai.intent_recognition.model.IntentRecognitionResult;
import com.weiqiang.skyai.intent_recognition.model.IntentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatClientCustomerIntentRecognitionClient implements CustomerIntentRecognitionClient {

    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            你是外卖客服意图识别器。
            任务：根据用户消息和对话历史，识别一个最合适的客服意图，并提取相关实体。
            ### 核心约束（极其重要）：
            1. 严禁输出任何解释性文字、分析过程或开场白。
            2. 响应必须以 "{" 开头，以 "}" 结尾，确保是纯粹的 JSON 格式。
            3. 不要使用 Markdown 代码块格式（即不要包含 ```json 标签）。
            4. 所有的实体提取必须严格基于对话内容。
            规则：
            1. 不确定时不要猜，优先返回 other 或最保守意图。
            2. 有歧义时把候选意图写入 possible_intents ，不要强行只选一个。
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
            - menu_query
            - cart_management
            - address_management
            - shop_status
            - faq
            - escalate_to_human
            - other
            ### 正确响应示例：
            {
              "intent": "other",
              "possible_intents": ["other"],
              "confidence": "HIGH",
              "entities": {},
              "requires_human_confirmation": false,
              "human_confirmation_reason": "",
              "clarification_question": ""
            }
            
            ### 错误响应示例（绝对禁止）：
            我经过分析认为用户在问名字，结果如下：
            {"intent": "other"...}
            """;

    private final ChatClient.Builder gptChatClient;

    @Override
    public IntentRecognitionResult recognize(IntentRecognitionRequest request) {
        String userText = buildUserText(request);
        String rawResponse = gptChatClient.build()
                .prompt()
                .system(s -> s.text(SYSTEM_PROMPT))
                .user(userText)
                .call()
                .content();
        log.debug("AI 原始响应内容: {}", rawResponse);

        // 2. 核心修复逻辑：提取 JSON 部分
        String jsonContent = extractJson(rawResponse);

        try {
            // 3. 手动使用 Jackson 反序列化，不要再用 BeanOutputConverter 的默认行为
            return objectMapper.readValue(jsonContent, IntentRecognitionResult.class);
        } catch (Exception e) {
            log.error("解析 JSON 失败。处理后的内容: {}", jsonContent, e);
            // 返回兜底逻辑，防止整个链路崩溃
            return new IntentRecognitionResult(IntentType.OTHER, ConfidenceLevel.LOW, Map.of(), List.of(), "抱歉，我刚才走神了，您能再详细说一遍吗？", false, null);
        }
    }

    private String extractJson(String text) {

        if (text == null || !text.contains("{")) {
            return "{}";
        }
        try {
            int firstOpenBrace = text.indexOf("{");
            int lastCloseBrace = text.lastIndexOf("}");
            if (firstOpenBrace >= 0 && lastCloseBrace > firstOpenBrace) {
                return text.substring(firstOpenBrace, lastCloseBrace + 1);
            }
        } catch (Exception e) {
            log.warn("提取 JSON 字符串片段失败: {}", text);
        }
        return text;
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
