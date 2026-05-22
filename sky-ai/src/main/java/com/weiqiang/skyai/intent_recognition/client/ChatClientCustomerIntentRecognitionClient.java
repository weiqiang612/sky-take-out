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

                ### 核心约束
                1. 严禁输出任何解释性文字、分析过程或开场白。
                2. 响应必须以 "{" 开头，以 "}" 结尾，且只能是纯 JSON。
                3. 不要使用 Markdown 代码块。
                4. 实体必须严格来源于对话内容，不得编造。
                5. entities 必须是扁平的 Map<String, String>，不得嵌套对象或数组。

                ### 识别规则
                1. 不确定时优先返回 `other`，不要硬猜。
                2. 如果是代词消解、历史引用、地址复用等场景，必须先看对话历史。
                3. 如果用户明确提到多个独立目标：
                   - 用 `possible_intents` 按执行顺序输出多个任务意图。
                   - 主 intent 设为第一个或最核心的意图。
                4. 如果用户是同一动作但多个目标：
                   - 保持单一 `intent`。
                   - 用扁平实体表达多个目标，尤其是 `cancel_order`。
                   - `cancel_order` 的多订单实体必须使用 `order_ids`，例如 `{"order_ids":"1779351452612,1779341664613"}`。
                5. 如果用户说的是“最近的几个/两个未送达订单，然后取消/退款”这类需要先查单再执行的请求：
                   - 不要编造真实订单号，也不要写 `LATEST_ORDER` 来凑数。
                   - 用 `possible_intents` 表达执行顺序，通常是 `order_status` 在前、最终操作在后。
                   - 在 entities 中提供查询线索，例如 `{"order_count":"2","order_status":"not_delivered"}`。
                   - 这种场景下 `requires_human_confirmation` 先保持 `false`，由后端先查询再在确认阶段展示真实订单号。
                6. 高风险意图：`cancel_order`、`request_refund`、`change_address`、`report_missing_item`
                   - 如果已经拿到可执行的真实目标实体，必须设置 `requires_human_confirmation=true`。
                   - `human_confirmation_reason` 必须说明具体原因。
                   - `clarification_question` 必须为空字符串 `""`。
                   - 如果需要先查单才能得到目标，不要提前把它当作已确认的可执行操作。
                7. 所有未填写的字符串字段都必须输出为空字符串 `""`。

                ### 意图示例
                - `order_status`: 查询订单状态、进度、是否出餐
                - `cancel_order`: 取消订单
                - `request_refund`: 申请退款
                - `reorder`: 再来一单
                - `track_delivery`: 催单、查配送员位置
                - `report_missing_item`: 订单缺少商品
                - `change_address`: 修改配送地址
                - `address_management`: 管理地址簿
                - `menu_query`: 查询菜品、套餐、价格
                - `cart_management`: 管理购物车
                - `shop_status`: 查询门店营业状态
                - `escalate_to_human`: 转人工
                - `faq`: 门店政策、退款流程等知识问答
                - `other`: 其他无法判断的情况

                ### 正确输出示例
                1. 单订单取消：
                {
                  "intent": "cancel_order",
                  "possible_intents": ["cancel_order"],
                  "confidence": "HIGH",
                  "entities": {"order_id": "1779262915511"},
                  "requires_human_confirmation": true,
                  "human_confirmation_reason": "取消订单属于高风险操作，请确认是否继续。",
                  "clarification_question": ""
                }

                2. 多订单取消：
                {
                  "intent": "cancel_order",
                  "possible_intents": ["cancel_order"],
                  "confidence": "HIGH",
                  "entities": {"order_ids": "1779351452612,1779341664613"},
                  "requires_human_confirmation": true,
                  "human_confirmation_reason": "您要求取消两个订单，请确认是否继续。",
                  "clarification_question": ""
                }

                3. 先查再退：
                {
                  "intent": "cancel_order",
                  "possible_intents": ["order_status", "cancel_order"],
                  "confidence": "HIGH",
                  "entities": {"order_count": "2", "order_status": "not_delivered"},
                  "requires_human_confirmation": false,
                  "human_confirmation_reason": "",
                  "clarification_question": ""
                }

                4. 缺少关键信息且无法从历史推断时：
                {
                  "intent": "other",
                  "possible_intents": ["other"],
                  "confidence": "LOW",
                  "entities": {},
                  "requires_human_confirmation": false,
                  "human_confirmation_reason": "",
                  "clarification_question": "请补充你要操作的订单号或更多信息。"
                }
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
