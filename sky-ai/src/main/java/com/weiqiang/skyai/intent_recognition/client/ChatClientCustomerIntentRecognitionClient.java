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
        4. 所有的实体提取必须严格基于对话内容，不得编造。

        ### 规则：
        1. 不确定时不要猜，优先返回 other 或最保守意图。
        2. 有歧义时把候选意图写入 possible_intents，不要强行只选一个。
        3. confidence 低时必须给出 clarification_question。
        4. cancel_order、request_refund、change_address、report_missing_item 这类高风险操作必须标记 requires_human_confirmation=true，并在 human_confirmation_reason 中说明原因。
        5. entities 只返回字符串键值对，优先提取 order_id、item_name、address 等业务实体。
        6. 只返回可被 JSON 反序列化的结构化结果，不要输出解释性文本。

        ### 意图说明与示例：

        **订单类：**
        - order_status：查询订单状态、进度、是否出餐
          示例："我的订单到哪了""订单什么时候送到"
        - cancel_order：取消订单（高风险，需确认）
          示例："我要取消订单""帮我取消一下"
        - request_refund：申请退款（高风险，需确认）
          示例："我要退款""这个订单我不想要了要退钱"
        - reorder：按历史订单重新下单
          示例："再来一单""还要一样的""按上次的帮我下单""我要重新点一次"
        - track_delivery：催单、查配送员位置
          示例："外卖怎么还没到""帮我催一下""配送员在哪"
        - report_missing_item：反映订单缺少商品（高风险，需确认）
          示例："我的订单少了一个菜""东西没收到"

        **地址类：**
        - change_address：修改某笔订单的配送地址（高风险，需确认）
          示例："帮我改一下这个订单的地址""地址填错了"
        - address_management：管理地址簿，如新增、修改、设置默认地址
          示例："我要加一个新地址""把默认地址换一下""修改我的收货地址"

        **菜单类：**
        - menu_query：查询菜品、套餐信息、价格、是否有某道菜
          示例："你们有什么菜""红烧肉多少钱""有没有素食"
        - cart_management：管理购物车，加菜、删菜、清空购物车
          示例："帮我加一份宫保鸡丁""把购物车清空""删掉刚才加的那个"

        **门店类：**
        - shop_status：查询门店是否营业、营业时间
          示例："你们现在开着吗""几点关门"
        - escalate_to_human：用户明确要求转人工客服
          示例："我要找人工""转人工""让真人来处理"

        **知识类：**
        - faq：询问门店政策、退款流程、配送规则等知识性问题
          示例："退款一般几天到账""配送费怎么算""超时了怎么办"

        **兜底：**
        - other：无法判断意图，或信息不足以确定意图，或与外卖业务无关的闲聊
          示例："你好""随便""天气真好"

        ### 意图区分说明：
        - reorder vs cart_management：reorder 是"按历史订单重新下单"，用户通常提到"上次""一样的""再来一单"；cart_management 是用户主动指定具体菜品操作购物车。
        - change_address vs address_management：change_address 是修改某笔已有订单的配送地址；address_management 是管理用户自己的地址簿。
        - faq vs other：faq 是询问与外卖业务相关的知识性问题；other 是完全无法判断或与业务无关。

        ### 高风险意图一览（必须设置 requires_human_confirmation=true）：
        - cancel_order
        - request_refund
        - change_address
        - report_missing_item

        ### 正确响应示例：
        {
          "intent": "reorder",
          "possible_intents": ["reorder"],
          "confidence": "HIGH",
          "entities": {"order_id": "123456"},
          "requires_human_confirmation": false,
          "human_confirmation_reason": "",
          "clarification_question": ""
        }

        ### 错误响应示例（绝对禁止）：
        - 不要输出省略内容，比如 ...
        - 不要输出单独的字符串或缺少键的值
        - 每个字段都必须完整按照 JSON key-value 形式输出
        - 不要对高风险意图漏设 requires_human_confirmation=true
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
