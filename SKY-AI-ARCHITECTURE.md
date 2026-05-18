# Sky-AI 客服Agent运作流程详解

## 1. 整体架构概览

Sky-AI是一个Spring AI驱动的智能客服Agent系统，采用**Advisor链**（顾问链）的架构，层层递进地完成"意图识别→上下文注入→工具过滤→LLM调用"的完整流程。

```
用户请求
   ↓
ChatController (/ai/ask)
   ↓
[Advisor Chain]
   ├─ IntentRecognitionAdvisor      (优先级最高: HIGHEST_PRECEDENCE)
   ├─ UserContextAdvisor            (优先级: HIGHEST_PRECEDENCE + 1)
   ├─ MessageChatMemoryAdvisor      (Spring AI内置)
   ├─ QuestionAnswerAdvisor         (RAG模块)
   ├─ ToolFilterAdvisor             (优先级: HIGHEST_PRECEDENCE + 4)
   └─ ChatClient                    (LLM调用)
   ↓
MemoryWriterService (异步持久化)
   ↓
最终回复
```

---

## 2. 核心流程详解

### 2.1 入口点：ChatController

```java
@RestController
@RequestMapping("/ai")
public class ChatController {
    @GetMapping("/ask")
    public Map<String, String> ask(
        @RequestParam("question") String question,
        @RequestParam(value = "conversationId", defaultValue = DEFAULT) String conversationId,
        @RequestParam(value = "userId", defaultValue = "anonymous") String userId
    )
}
```

**关键点：**
- 接收用户问题、会话ID、用户ID
- 首先调用`agentChatService.recognizeIntent()`进行**预识别**
- 根据识别结果决定后续流程

---

### 2.2 Advisor Chain工作流

#### 2.2.1 IntentRecognitionAdvisor（意图识别）

**职责：** 识别用户问题的意图类型

**工作流程：**
1. 优先检查Controller是否已预识别（`preRecognizedIntent`）
   - 如果有，直接用预识别结果，避免重复调用
2. 如果没有，调用`CustomerIntentRecognitionClient.recognize()`
   - 收集最近4条聊天记录（从Redis）
   - 收集用户的已知问题摘要（从PostgreSQL）
   - 将这些上下文传给LLM进行意图分类

3. 构造`IntentRecognitionResult`，放入`advisorContext`的`"intentResult"`键

**识别的意图类型：**
```java
enum IntentType {
    ORDER_STATUS,           // 查询订单状态
    CANCEL_ORDER,           // 取消订单
    REQUEST_REFUND,         // 申请退款
    TRACK_DELIVERY,         // 追踪配送
    REPORT_MISSING_ITEM,    // 报告遗漏
    CHANGE_ADDRESS,         // 修改地址
    MENU_QUERY,             // 菜单查询
    CART_MANAGEMENT,        // 购物车管理
    ADDRESS_MANAGEMENT,     // 地址管理
    SHOP_STATUS,            // 店铺状态
    FAQ,                    // 常见问题（触发RAG）
    ESCALATE_TO_HUMAN,      // 人工转接
    OTHER                   // 其他
}
```

**信心度分级：**
```java
enum ConfidenceLevel {
    HIGH,    // 高置信度 → 注入完整上下文
    MEDIUM,  // 中置信度 → 注入部分上下文
    LOW      // 低置信度 → 不注入上下文，仅返回澄清问题
}
```

---

#### 2.2.2 UserContextAdvisor（用户上下文注入）

**职责：** 根据意图和用户记忆，注入相关上下文到系统提示中

**工作流程：**

1. **读取意图识别结果**
   ```java
   IntentRecognitionResult intentResult = 
       chatClientRequest.context().get("intentResult");
   ```

2. **计算允许的工具集合**
   - 根据`IntentType`映射到对应的工具列表
   - 例如：`CANCEL_ORDER` → {searchOrders, cancelOrder}
   - 放入`advisorContext`的`"allowedTools"`键
   - 供后续`ToolFilterAdvisor`使用

3. **构建上下文块（Context Block）**
   - 根据意图类型，从数据库查询相关记忆信息
   - 限制长度≤5句话，保证LLM上下文不爆炸

**意图→记忆字段的映射表：**

| 意图 | 关键记忆字段 | 示例上下文 |
|------|------------|---------|
| ORDER_STATUS | 最近订单ID | Order id: 1234567 |
| CANCEL_ORDER | 操作记录(OPERATIONAL_NOTES) | Known issues: 上次订单已成功取消 |
| REQUEST_REFUND | 操作记录 | Known issues: 该用户有退款历史 |
| CHANGE_ADDRESS | 默认地址 | Default address: 朝阳区建国路1号 |
| MENU_QUERY | 偏好菜品、偏好口味、饮食限制 | Favorite dishes: 鱼香肉丝; Dietary restrictions: 无辣 |
| CART_MANAGEMENT | 偏好菜品、偏好口味、饮食限制 | Favorite flavors: 清淡 |
| ADDRESS_MANAGEMENT | 默认地址 | Default address: ... |
| FAQ | 偏好菜品等 | (从RAG检索) |
| OTHER | 默认地址 | (如果信心度MEDIUM或HIGH) |

**示例上下文注入：**
```
Relevant memory:
- Favorite dishes: 鱼香肉丝, 回锅肉 [用户自设]
- Dietary restrictions: 无辣
- Default address: 朝阳区建国路1号

If the user names a dish, search the menu first and then act on the unique match directly.
```

---

#### 2.2.3 MessageChatMemoryAdvisor（会话记忆）

**职责：** 管理会话级别的消息历史

- Spring AI内置组件
- 从Redis读取/写入会话消息
- TTL: 2小时（自动刷新）
- 使用`conversationId`作为键

---

#### 2.2.4 QuestionAnswerAdvisor（RAG模块）

**职责：** 处理FAQ类意图

- 使用向量数据库检索相关文档
- 实现RAG（Retrieval-Augmented Generation）
- 当`IntentType == FAQ`时触发

**配置项：**
```yaml
skyai:
  retrieval:
    online:
      top-k: 80           # 从向量库取多少条
      top-n: 8            # 重排后返回多少条
      similarity-threshold: 0.0
      query-expansion:
        enabled: true     # 启用查询扩展
        max-queries: 2
      keyword:
        enabled: true     # 启用关键词检索
        top-k: 40
      fusion:
        max-candidates: 120  # 混合排序
```

---

#### 2.2.5 ToolFilterAdvisor（工具过滤）

**职责：** 根据允许的工具集合，过滤并注册可用工具

**工作流程：**
1. 从`advisorContext`读取`"allowedTools"`（由UserContextAdvisor设置）
2. 调用`DynamicToolCallbackRegistry.selectCallbacks(allowedTools)`
3. 只注册允许的工具到LLM

**可用工具列表（OrderTools + CartTools + MenuTools + AddressTools）：**

| 工具类 | 工具方法 | 说明 |
|------|--------|------|
| OrderTools | searchOrders | 按关键词搜索订单 |
| | getOrderDetail | 获取订单详情 |
| | listRecentOrders | 列出最近订单 |
| | cancelOrder | 取消订单 |
| | requestRefund | 申请退款 |
| | updateDeliveryAddress | 修改配送地址 |
| | remindOrder | 催单 |
| CartTools | searchDishes | 搜索菜品 |
| | searchSetmeals | 搜索套餐 |
| | listCart | 查看购物车 |
| | addDishToCart | 加菜到购物车 |
| | removeCartItem | 从购物车移除 |
| | cleanCart | 清空购物车 |
| MenuTools | listCategories | 菜品分类列表 |
| | listDishesByCategory | 按分类查菜品 |
| | getShopStatus | 获取店铺营业状态 |
| AddressTools | searchAddresses | 搜索地址簿 |
| | listAddresses | 列表地址 |
| | setDefaultAddress | 设默认地址 |
| | updateAddress | 修改地址 |

---

### 2.3 工具调用（Tool Calling）

#### 2.3.1 工具网关（Gateway）

所有工具最终通过`OrderGateway`/`CartGateway`等调用sky-server暴露的`/ai/customer/**`接口

```
OrderTools
    ↓
OrderGateway (HTTP客户端)
    ↓
Sky-Server: /ai/customer/orders/{id}
    ↓
返回JSON结果
```

**调用示例：**
```java
@Tool(description = "Cancel an unpaid or unconfirmed order")
public String cancelOrder(String orderRef, ToolContext context) {
    Long orderId = resolveOrderId(orderRef, context);
    return orderGateway.cancelOrder(ToolUser.userId(context), orderId.toString());
}
```

#### 2.3.2 ToolContext（工具上下文）

工具执行时的上下文对象，包含：
- `userId`: 当前用户ID
- `conversationId`: 会话ID
- 其他自定义信息

**示例提取用户ID：**
```java
String userId = ToolUser.userId(context);
```

---

## 3. 内存系统详解

Sky-AI采用**三层内存架构**：

### 3.1 Working Memory（工作记忆）

**存储地点：** 请求的上下文（In-Memory）
**时效性：** 当前请求周期
**用途：** 存储最近4条聊天消息、意图识别结果

**示例：**
```
advisorContext = {
    "intentResult": IntentRecognitionResult,
    "allowedTools": Set<String>,
    "userId": "user123",
    "conversationId": "conv-456"
}
```

---

### 3.2 Session Memory（会话记忆）

**存储地点：** Redis
**键格式：** `chat:{conversationId}`
**TTL：** 2小时（每次访问刷新）
**容量：** 最近20条消息

**工作流程：**
1. `ChatHistoryService.buildHistory()` 从Redis读取最近4条消息
2. LLM识别意图、调用工具、生成回复
3. `MemoryWriterService.writeTurn()` 异步把新消息保存回Redis

**数据结构：**
```json
{
  "messages": [
    {"type": "user", "text": "我想取消订单123"},
    {"type": "assistant", "text": "我帮你处理..."},
    {"type": "tool", "name": "cancelOrder", "result": "成功取消"}
  ]
}
```

---

### 3.3 Long-term Memory（长期记忆）

**存储地点：** PostgreSQL（JPA实体）
**表名：** `user_memory_facts`
**字段：** userId, factKey, factValue, sourceType, updatedAt

**记忆事实类型（MemoryFactKey）：**

| 键 | 含义 | 示例值 |
|----|------|-------|
| FAVORITE_DISHES | 偏好菜品 | ["鱼香肉丝", "回锅肉"] |
| FAVORITE_FLAVORS | 偏好口味 | "清淡" |
| DIETARY_RESTRICTIONS | 饮食限制 | "无辣，无花生" |
| DEFAULT_ADDRESS | 默认地址 | "朝阳区建国路1号" |
| OPERATIONAL_NOTES | 操作记录 | "上次订单异常，已补偿" |

**来源类型（MemoryFactSourceType）：**
- `USER_EXPLICIT`: 用户明确说出的 → LLM从转录文本提取
- `USER_MANUAL`: 用户在UI中手动设置 → 直接保存
- `INFERRED`: 系统推断的 → 从工具调用结果提取

**工作流程：**
1. 对话结束，触发`MemoryWriterService.writeTurn()`
2. 检查`requiresHumanConfirmation`
   - 如果是，仅保存Redis会话记录，不写长期记忆
   - 如果否，继续
3. 收集所有`ToolResponseMessage`，调用`persistToolOutcomes()`
   - 例如：cancelOrder成功 → 追加到OPERATIONAL_NOTES
4. 调用LLM进行记忆提取
   - 系统提示：指导LLM返回JSON格式的事实
   - 用户消息：当前转录文本
   - LLM输出示例：
     ```json
     {
       "favorite_dishes": {"value": ["平菇豆腐汤"], "confidence": 1.0},
       "favorite_flavors": {"value": "清淡", "confidence": 0.9},
       "corrections": ["favorite_flavors"]
     }
     ```
5. 合并或更新数据库中的事实

---

## 4. 意图识别流程详解

### 4.1 识别客户端

```java
public interface CustomerIntentRecognitionClient {
    IntentRecognitionResult recognize(IntentRecognitionRequest request);
}
```

**实现：** `ChatClientCustomerIntentRecognitionClient`

### 4.2 识别请求结构

```java
record IntentRecognitionRequest(
    String userText,           // 用户问题
    List<String> history       // 最近4条消息 + 已知问题摘要
) {}
```

### 4.3 识别结果结构

```java
record IntentRecognitionResult(
    IntentType intent,                          // 识别的意图
    ConfidenceLevel confidence,                 // 信心度
    Map<String, String> entities,               // 提取的实体（如order_id）
    List<IntentType> possibleIntents,           // 其他可能的意图
    String clarificationQuestion,               // 澄清问题（低置信度时）
    boolean requiresHumanConfirmation,          // 是否需要人工确认
    String humanConfirmationReason              // 人工确认原因
) {}
```

### 4.4 低置信度澄清逻辑

当`confidence == LOW`：
1. LLM生成`clarificationQuestion`
2. Controller直接返回澄清问题给用户，不执行工具调用
3. 用户补充信息后，重新发起请求

**示例：**
```
用户: "帮我取消那个订单"
LLM低置信度识别 → 返回: "你是想取消哪个订单？请提供订单号"
用户: "订单号是123456"
重新识别 → 高置信度 → 执行cancelOrder工具
```

---

## 5. 工具调用与MCP集成

### 5.1 本地工具（@Tool注解）

通过Spring AI的`@Tool`注解，在OrderTools/CartTools等组件中定义

```java
@Component
public class OrderTools {
    @Tool(description = "Cancel an unpaid or unconfirmed order")
    public String cancelOrder(
        @ToolParam(description = "Order ID") String orderRef,
        ToolContext context
    ) {
        // ...
    }
}
```

### 5.2 MCP（Model Context Protocol）服务器

在`application.yml`配置MCP服务器：

```yaml
spring:
  ai:
    mcp:
      client:
        sse:
          connections:
            maps-server:
              url: ${MCP_MAPS_URL:}
            payments-server:
              url: ${MCP_PAYMENTS_URL:}
            notifications-server:
              url: ${MCP_NOTIFICATIONS_URL:}
```

**MCP集成的优势：**
- 工具由独立服务提供（解耦）
- Spring AI自动发现和注册工具
- 与本地`@Tool`方法无缝配合

**MCP工具示例：**
- maps-server: 地理位置相关工具
- payments-server: 支付相关工具
- notifications-server: 通知相关工具

---

## 6. 完整交互示例

### 场景：用户取消订单

```
请求：
  GET /ai/ask?question=帮我取消订单123&conversationId=conv-001&userId=user-001

↓

1. ChatController.ask()
   ├─ 调用 agentChatService.recognizeIntent()
   │  └─ 返回: IntentRecognitionResult(CANCEL_ORDER, HIGH, {order_id: "123"}, ...)
   └─ 调用 agentChatService.ask(question, conversationId, userId, preIntent)

↓

2. Advisor Chain 执行顺序：

   a) IntentRecognitionAdvisor
      - 已有preRecognizedIntent，直接使用
      - 将result放入context["intentResult"]

   b) UserContextAdvisor
      - 读取intentResult = CANCEL_ORDER
      - 计算allowedTools = {searchOrders, cancelOrder}
      - 放入context["allowedTools"]
      - 从DB查询OPERATIONAL_NOTES
      - 注入系统提示

   c) MessageChatMemoryAdvisor
      - 从Redis读取conversationId的历史消息

   d) QuestionAnswerAdvisor
      - CANCEL_ORDER不是FAQ，跳过

   e) ToolFilterAdvisor
      - 从context读取allowedTools
      - 只注册这两个工具到LLM

   f) ChatClient
      - 调用LLM（OpenAI）
      - LLM决定调用cancelOrder工具
      - OrderTools.cancelOrder()执行
      - 调用sky-server: PUT /ai/customer/orders/123/cancel
      - 返回: "订单123已取消"
      - LLM生成最终回复

↓

3. MemoryWriterService.writeTurn() 异步执行
   - 保存新消息到Redis (conversationId的消息列表)
   - 提取cancelOrder工具的结果
   - 追加到user-001的OPERATIONAL_NOTES: "Cancelled order 123 on 2026-05-18"
   - 更新user_memory_facts表

↓

4. 返回给客户端：
   {
     "question": "帮我取消订单123",
     "answer": "好的，我已经为你取消了订单123。"
   }
```

---

## 7. 关键设计决策

### 7.1 为什么使用Advisor链？

✅ **优点：**
- 职责分离：每个Advisor做一件事
- 易于扩展：添加新Advisor无需修改现有代码
- 灵活组合：可通过order()控制执行顺序
- Spring AI标准：遵循框架约定

❌ **传统做法的问题：**
```java
// 不推荐：一个大Service混合所有逻辑
class ChatService {
    - recognizeIntent()
    - injectContext()
    - filterTools()
    - callLLM()
    - persistMemory()
}
```

### 7.2 为什么分离预识别和链内识别？

```java
// Controller预识别
IntentRecognitionResult preIntent = agentChatService.recognizeIntent(question);

if (preIntent.clarificationQuestion != null) {
    return clarificationQuestion;  // ← 快速路径，避免整个链执行
}

// 链内再识别
IntentRecognitionAdvisor {
    IntentRecognitionResult result = 
        preRecognizedIntent != null ? preRecognizedIntent : recognize();
}
```

**优点：**
- 低置信度时可立即返回澄清问题
- 避免不必要的工具调用
- 减少延迟

### 7.3 为什么使用@Async持久化？

```java
@Service
public class MemoryWriterService {
    @Async
    public void writeTurn(String userId, String conversationId, ...) {
        // 不阻塞API响应
    }
}
```

**优点：**
- API响应延迟不受DB写入影响
- 并发能力强
- 内存提取失败不影响用户体验

**风险：**
- 可能丢失内存（但降级可接受）
- 需要异步错误处理

---

## 8. 调试和监控

### 8.1 关键日志点

```java
// IntentRecognitionAdvisor
log.debug("认识意图: {} 信心度: {}", result.intent(), result.confidence());

// UserContextAdvisor
log.info("UserContextAdvisor intentResult: {}", intentResult);
log.info("Computed allowedTools: {}", allowedTools);

// ToolFilterAdvisor
log.info("ToolFilterAdvisor tools count={}, tools={}", 
    allowedTools.size(), previewTools(allowedTools));

// MemoryWriterService
log.debug("extracted memory facts userId={} favorite_dishes.value={} ...", userId, ...);
```

### 8.2 调试技巧

**1. 打印advisorContext**
```java
// 在任何Advisor中
log.info("Current context: {}", chatClientRequest.context().keySet());
```

**2. Redis会话查询**
```bash
redis-cli
KEYS "chat:*"
GET "chat:conv-001"
```

**3. PostgreSQL记忆查询**
```sql
SELECT * FROM user_memory_facts WHERE user_id = 'user-001';
```

**4. HTTP工具调用日志**
```java
// OrderGateway中启用调试
okhttp3.logging.HttpLoggingInterceptor
```

---

## 9. 扩展点

### 9.1 添加新的Advisor

1. 实现`CallAdvisor`接口
2. 标注`@Component`
3. 实现`adviseCall()`和`adviseStream()`
4. 通过`getOrder()`控制顺序

```java
@Component
public class MyNewAdvisor implements CallAdvisor, StreamAdvisor {
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // 修改request或链路
        return chain.nextCall(request);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;  // 在这个位置执行
    }
}
```

### 9.2 添加新的意图类型

1. 在`IntentType`枚举中添加新值
2. 在`UserContextAdvisor.buildContextBlock()`中处理
3. 在`UserContextAdvisor.allowedTools()`中映射工具
4. 更新LLM提示词

### 9.3 添加新的记忆类型

1. 在`MemoryFactKey`中添加新键
2. 在`MemoryWriterService.EXTRACTION_SYSTEM_PROMPT`中指导LLM
3. 在`UserContextAdvisor.formatMemoryLine()`中格式化显示

---

## 10. 常见问题

**Q: 意图识别失败会怎样？**
A: 返回默认的`IntentType.OTHER`，`ConfidenceLevel.LOW`，不注入上下文，不调用工具。

**Q: 工具调用超时怎么办？**
A: Sky-server网关调用超时或返回错误，LLM收到失败响应，可能重试或告知用户。

**Q: 内存提取失败怎么办？**
A: `MemoryWriterService`是@Async的，异常不会影响API响应，但会丢失本次提取的内存。

**Q: 如何清除用户的长期记忆？**
A: 直接删除`user_memory_facts`表中该用户的记录，或在UI提供清除选项。

**Q: 如何支持多语言？**
A: LLM和意图枚举已支持，需在提示词中指定语言。

---

这就是Sky-AI的完整运作流程！核心是Advisor链的巧妙组织，每一环都可独立扩展。

