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
            2. 有歧义时把候选意图写入 possible_intents，不要强行只选一个。
            3. confidence 低时必须给出 clarification_question。
            4. cancel_order、request_refund、change_address 这类高风险操作必须标记 requires_human_confirmation=true，并说明原因。
            5. entities 只返回字符串键值对，优先提取 order_id、item_name、address 等业务实体。
            6. 只返回可被 JSON 反序列化的结构化结果，不要输出解释性文本。

            可用意图：
            - order_status
            - cancel_order
            - request_refund
            - reorder
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
            - 不要输出省略内容，比如 ...
            - 不要输出单独的字符串或缺少键的值
            - 每个字段都必须完整按照 JSON key-value 形式输出
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

        String jsonContent = repairJsonContent(rawResponse);

        try {
            return objectMapper.readValue(jsonContent, IntentRecognitionResult.class);
        } catch (Exception e) {
            log.error("解析 JSON 失败。处理后的内容: {}", jsonContent, e);
            return new IntentRecognitionResult(
                    IntentType.OTHER,
                    ConfidenceLevel.LOW,
                    Map.of(),
                    List.of(),
                    "抱歉，我刚才走神了，您能再详细说一遍吗？",
                    false,
                    null
            );
        }
    }

    static String repairJsonContent(String text) {
        if (!StringUtils.hasText(text)) {
            return "{}";
        }

        String candidate = text.replace("```json", "").replace("```", "");
        String json = extractJson(candidate);
        if (!StringUtils.hasText(json)) {
            return "{}";
        }

        StringBuilder repaired = new StringBuilder();
        String[] lines = json.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (trimmed.equals("{") || trimmed.equals("}") || trimmed.equals("[") || trimmed.equals("]")) {
                repaired.append(line).append('\n');
                continue;
            }
            if (trimmed.startsWith("\"") && trimmed.endsWith("\",") && !trimmed.contains(":")) {
                log.warn("修复 AI 输出时移除了未指定键的字符串行: {}", trimmed);
                continue;
            }
            repaired.append(line).append('\n');
        }

        String normalized = repaired.toString().replaceAll(",\\s*([}\\]])", "$1").trim();
        return StringUtils.hasText(normalized) ? normalized : "{}";
    }

    static String extractJson(String text) {
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

    static String buildUserText(IntentRecognitionRequest request) {
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
