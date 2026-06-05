# Sky-AI 智能客服 Agent 架构详解与运作流程

Sky-AI 是一个基于 **Spring AI (v3.4.13)** 驱动的先进智能客服 Agent 系统。其核心架构围绕 **Advisor 链（顾问链）** 深度展开，融合了长连接双向实时通信、基于规则的高级任务规划与分解编排、以及强一致性的混合记忆持久化系统。通过精密的卡点控制和安全防线设计，系统实现了从“多意图语义分析”到“跨步骤级联调用和人工干预确认”的工业级闭环运作。

---

## 1. 整体架构概览

Sky-AI 采用分层拓扑与事件/长连接双向流式驱动，整体架构如下图所示：

```
                    ┌──────────────────────────────────────┐
                    │        客户端 (Web / WebSocket)      │
                    └───────┬──────────────────────▲───────┘
                            │ (Request / Text Frame)│ (Delta Token / Control Frame)
                            ▼                       │
               ┌────────────────────────┐           │
               │   WebSocket 实时入口   │           │
               │ (AgentChatWebSocket)   │           │
               └────────────┬───────────┘           │
                            │ 1. 意图预识别         │
                            ▼                       │
               ┌────────────────────────┐           │
               │  任务编排与规划中心    │           │
               │(TaskOrchestratorService)           │
               └────────────┬───────────┘           │
                            │ 2. 任务流分解计划     │
                            ▼                       │
               ┌────────────────────────┐           │
               │     顾问链装配驱动     │           │
               │   (AgentChatService)   │           │
               └────────────┬───────────┘           │
                            │                       │
 ┌──────────────────────────┼───────────────────────┼─────────────────────────┐
 │ Advisor Chain (顾问链)   ▼                       │                         │
 │                                                  │                         │
 │  ├─ 1. IntentRecognitionAdvisor  (HIGHEST_PRECEDENCE)                      │
 │  │     └─ 意图二度识别；根据配置动态将 User Profile Summary 注入输入以辅助语义理解│
 │  │                                                                         │
  │  ├─ 2. FaqSemanticCacheAdvisor   (HIGHEST_PRECEDENCE + 1)                  │
  │  │     └─ FAQ 语义缓存拦截；在本地相似度命中时直接短路答复以节省大模型 Token       │
  │  │                                                                         │
  │  ├─ 3. UserContextAdvisor       (HIGHEST_PRECEDENCE + 1)                  │
 │  │     └─ 根据识别意图自适应评估画像注入级别 (NONE/SUMMARY/FULL) 并计算两级 allowedTools  │
 │  │                                                                         │
  │  ├─ 4. MessageChatMemoryAdvisor (Spring AI 内置)                           │
 │  │     └─ 基于会话 ID 从 RedisChatMemoryRepository 加载最近历史消息上下文              │
 │  │                                                                         │
  │  ├─ 5. RagAdvisor               (条件挂载：HIGHEST_PRECEDENCE + 3)         │
 │  │     └─ 针对特定知识库及纠纷高风险意图 (shouldUseRag)，注入向量检索关联文本             │
 │  │                                                                         │
  │  ├─ 6. ToolFilterAdvisor         (HIGHEST_PRECEDENCE + 4)                  │
 │  │     └─ 基于前面算出的 allowedTools，对当前回合 LLM 可见工具进行硬性筛选与回调绑定      │
 │  │                                                                         │
  │  └─ 7. SafeToolCallAdvisor       (安全防线：LOWEST_PRECEDENCE - 100)       │
 │        └─ 跟踪工具参数签名，拦截无限死循环；超过 4 轮或有重复签名则执行 fallback 截断      │
 └──────────────────────────┬─────────────────────────────────────────────────┘
                            │ 3. 执行工具 / 最终调用
                            ▼
               ┌────────────────────────┐
               │    LLM (ChatClient)    │
               └────────────┬───────────┘
                            │ 4. 异步持久化触发
                            ▼
               ┌────────────────────────┐
               │  异步记忆写入服务      │
               │ (MemoryWriterService)  │
               └────────────┬───────────┘
                            │ (异步多线程 @Async)
                            ├─► [Redis] (会话上下文，TTL 2h)
                            └─► [PostgreSQL] (长期用户事实 user_memory_facts)
```

---

## 2. 顾问链（Advisor Chain）内部运作细节与安全防线

### 2.1 意图识别与 Profile 辅助理解 (IntentRecognitionAdvisor)
在首步执行中，`IntentRecognitionAdvisor` 的 `getOrder()` 为 `Ordered.HIGHEST_PRECEDENCE`。其工作流程为：
1. **预识别旁路**：优先检测上下文参数 `preRecognizedIntent`，如有值则直接使用并存入 `advisorContext` 的 `"intentResult"` 中，免于二次识别，以达到性能最优化。
2. **Profile 增强上下文**：当开启 `userProfileMemoryProperties.isIntentRecognitionSummaryEnabled()` 时，将调用 `UserMemoryFactService` 异步提取用户最新的 **Profile Summary（用户画像摘要）**。如果是已知老用户，系统会在传入意图识别客户端前，在用户当前输入的最前端拼接其画像备注摘要：
   ```
   User profile notes: [画像摘要]
   [用户当前提问文本]
   ```
   这一设计允许 LLM 即使在极简的一句话指令下（如“取消上次的操作”），也能准确识别出与画像事实强烈相关的意图。
3. **输入装配与调用**：将带有增强文本的请求与通过 `ChatHistoryService` 获取的 Redis 历史对话（最近 4 条）传入意图识别客户端，分类得到最终意图。

### 2.2 FAQ 语义缓存短路拦截 (FaqSemanticCacheAdvisor)
在第二步执行中，`FaqSemanticCacheAdvisor` 的 `getOrder()` 为 `Ordered.HIGHEST_PRECEDENCE + 1`（在顾问链中排在 `IntentRecognitionAdvisor` 之后，`UserContextAdvisor` 之前）。其主要职责和机制如下：
1. **意图匹配拦截**：判断上下文中的预识别意图 `preRecognizedIntent`，若其分类属于 `IntentType.FAQ`，则进行拦截处理，否则无感流转到后面的 Advisor。
2. **本地语义相似度匹配**：针对 FAQ 提问，使用配置好的 `EmbeddingModel` 对用户输入进行向量化（生成 Query Vector），随后调用 `FaqCacheManager` 在本地/Redisson 共享的 FAQ 缓存中进行向量相似度匹配。
3. **短路直接答复 (Short-circuit)**：若相似度分值满足设定阈值且命中缓存中已配好的 FAQ 条目，系统直接根据缓存数据手动封装包含 `AssistantMessage` 的 `ChatClientResponse` 并返回给交互侧，**从而彻底短路，跳过后续昂贵的 RAG 检索大模型推理流程**，在毫秒级完成常见问题的智能回复并实现 Token 的“零消耗”。

### 2.3 多维意图建模与两级工具计算模型 (UserContextAdvisor)
`UserContextAdvisor` 负责对意图进行解构，该类的 `getOrder()` 为 `Ordered.HIGHEST_PRECEDENCE + 1`。

#### 2.3.1 意图重写参数
当上下文存在 override 参数 `currentStepIntent` 时，顾问会自动将当前意图强设为 `HIGH` 置信度并复用原 `entities` 实体，同时设置 `skipProfileInjection = true`（跳过画像注入，以防止复杂分步任务中上下文冗余）。

#### 2.2.2 多维意图属性
意图模型 `IntentType` 在代码中进行了高维属性定义：
- **`highRisk`**：标志该操作是否属于高风险操作（如取消订单、申请退款、修改地址）。
- **`category`**：定义其语义类型，包括 `TASK`（任务型）、`KNOWLEDGE`（知识库型）以及 `CONVERSATIONAL`（闲聊或人工转接型）。
- **`domain`**：定义任务的业务范畴，包括 `ORDER`（订单域）、`MENU`（菜单域）、`ADDRESS`（地址域）、`SHOP`（店铺域）。

#### 2.3.3 用户画像注入等级控制 (Profile Injection Level)
为了在长会话中兼顾 LLM 的推理深度与 Token 消耗，顾问引入了细粒度的动态注入控制机制：
- **`NONE`**：完全不注入画像事实。适用于 `SHOP_STATUS` 意图。
- **`SUMMARY`**：仅注入用户画像摘要。适用于 `ORDER_STATUS`, `TRACK_DELIVERY`, `CANCEL_ORDER`, `REQUEST_REFUND`, `CHANGE_ADDRESS`, `ADDRESS_MANAGEMENT`, `REPORT_MISSING_ITEM`, `REORDER`。
- **`FULL`**：注入高保真的详细画像。适用于 `MENU_QUERY`, `CART_MANAGEMENT`, `FAQ`, `ESCALATE_TO_HUMAN`, `OTHER` 等语义依赖较重、信息多变的回合。

#### 2.3.4 两级工具计算与授权模型
当且仅当识别意图为 `isTask() == true` 时，系统才会通过两级计算，动态对 LLM 限制并授权可用的工具集，生成 `"allowedTools"` 塞入 `advisorContext`。
- **第一级：Domain 基础工具集（按业务域归类赋权）**
  - `ORDER` 域 ─► `searchOrders`, `getOrderDetail`, `listRecentOrders`
  - `MENU` 域 ─► `searchDishes`, `searchSetmeals`
  - `ADDRESS` 域 ─► `searchAddresses`, `listAddresses`
  - `SHOP` 域 ─► `getShopStatus`
- **第二级：Intent 专属工具集（按意图针对性叠加追加）**
  - `ORDER_STATUS` / `TRACK_DELIVERY` ─► 追加 `remindOrder`（催单）
  - `CANCEL_ORDER` ─► 追加 `cancelOrder`
  - `REQUEST_REFUND` / `REPORT_MISSING_ITEM` ─► 追加 `requestRefund`
  - `REORDER` ─► 追加 `addDishToCart`, `addSetmealToCart`, `reorder`
  - `CHANGE_ADDRESS` ─► 追加 `updateDeliveryAddress`
  - `MENU_QUERY` ─► 追加 `listCategories`, `listDishesByCategory`, `listSetmealsByCategory`, `listSetmealDishes`, `getShopStatus`
  - `CART_MANAGEMENT` ─► 追加 `searchCartItems`, `listCart`, `addDishToCart`, `addSetmealToCart`, `removeCartItem`, `cleanCart`
  - `ADDRESS_MANAGEMENT` ─► 追加 `getDefaultAddress`, `setDefaultAddress`, `updateAddress`

### 2.4 条件式 RAG 挂载保护 (RagAdvisor)
`RagAdvisor` 的 `getOrder()` 为 `Ordered.HIGHEST_PRECEDENCE + 3`。为防止通用的闲聊或简单工具调用回合遭受大量的无用文档片段轰炸，顾问在此实现了**条件式挂载**机制：
- 只有满足 `shouldUseRag` 判定（即意图分类为 `isKnowledge()` [如 `FAQ`]，或属于涉及业务流与政策纠纷的高风险意图 `CANCEL_ORDER`、`REQUEST_REFUND`、`REPORT_MISSING_ITEM`）时，才会挂载向量检索服务。
- 检索到结果后，将 `"使用以下检索上下文回答问题：\n"` 前缀与上下文拼接为 `SystemMessage` 优先 prepend 到指示序列中。

### 2.5 防工具调用死循环安全顾问 (SafeToolCallAdvisor)
由于大语言模型在解决多阶段复杂任务或面对模糊信息时，容易陷入反复执行相同工具的逻辑死循环中，系统特在顾问链的尾部（`LOWEST_PRECEDENCE - 100` 级别）部署了 `SafeToolCallAdvisor`，构建了最终的工业级安全防线：
- **工具调用签名跟踪**：跟踪当前回合中所有被调用的工具，以其名称和参数进行组合，构造工具签名：`Signature = toolCall.name() + "\u0000" + toolCall.arguments()`。
- **循环判定卡点**：如果出现以下任一判定：
  1. 当前回合新调用的工具签名与之前已被执行的工具签名**完全一致**（检测到重复输入调用循环）；
  2. 当前单回合的工具链式流转总轮数 `toolCallRounds` 达到上限门槛 `MAX_TOOL_CALL_ROUNDS`（默认设为 **4 轮**）；
- **强制阻断Fallback**：拦截该工具调用，重写 LLM 输出为安全兜底话术，并直接截断返回：
  > “已查询到的信息不足以继续自动处理，请你确认一下需要的菜品或改用更明确的说法。”

---

## 3. WebSocket 长连接交互协议与状态帧体系

系统核心通过 `AgentChatWebSocketHandler` 维护了轻量级、低延迟的 Web 客服通信链路。协议采用基于 JSON 承载的双向事件驱动架构，定义了如下核心**交互控制帧（Control Frames）**：

| 控制帧类型 (`type`) | 数据载荷参数 | 说明 |
| :--- | :--- | :--- |
| **`token`** | `content` (增量字符) | LLM 生成的流式增量 Token 推送，利用 AtomicReference 进行首尾剪切式 Delta 计算输出。 |
| **`confirmation`** | `intent`, `orderId`, `question`, `reason` | **人工确认交互帧**。用于截断执行流并让前端弹出强交互的确认弹窗。 |
| **`step_start`** | `stepNumber`, `intent` | 复杂任务开启分步执行的事件通知。 |
| **`step_done`** | `stepNumber`, `summary` | 复杂任务中单步完成事件，载荷内携带当前步骤的完成总结。 |
| **`plan_complete`** | `finalAnswer`, `totalSteps` | 复杂任务规划执行全部结束的汇总报告帧。 |
| **`done`** | `intent` | 当前单回合对话完全结束。 |
| **`error`** | `message` | 发生非预期异常。在超时场景下，自动匹配底层 `TimeoutException`，输出友好兜底：“本次回复超时了，请稍后重试，或换一种说法再试一次。” |
| **`cancelled`** | `conversationId` | 客户端发起中止，服务端调用 Disposable 及时注销当前流式 Subscription 并关闭当前的任务编排执行器。 |

---

## 4. 任务规划器与多步骤编排 (RuleBasedTaskPlanner)

客服系统的核心亮点之一是能够解决用户在一句话中布置的复杂多项业务。系统依靠 `TaskOrchestratorService` 协调，并通过 `RuleBasedTaskPlanner` 自动匹配并分解生成任务计划 `TaskPlan`（包含多个 `TaskStep`）。

```
        ┌────────────────────────────────────────────────────────┐
        │                 用户复杂语义输入                       │
        │ ("帮我退款订单123，并查一下最近的没送到的2个订单取消掉")│
        └──────────────────────────┬─────────────────────────────┘
                                   ▼
                   ┌──────────────────────────────┐
                   │    RuleBasedTaskPlanner      │
                   └───────────────┬──────────────┘
                                   │ 识别并应用如下三大分解模式
                                   ▼
     ┌─────────────────────────────┼─────────────────────────────┐
     ▼                             ▼                             ▼
【 模式一：复合意图任务分解 】    【 模式二：多订单批量取消 】  【 模式三：检索驱动取消规划 】
 提取 `possibleIntents` 中        如果主意图为 CANCEL_ORDER 且   针对模糊提问且无显式订单 ID，
 所有的 isTask() 意图组合。       拥有多个 explicit `order_ids`。首先生成 `ORDER_STATUS` 进行查询：
                                                                 注入参数 query_mode: recent_orders,
                                                                 order_status: not_delivered.
                                                                 后续步骤绑定为 CANCEL_ORDER，参数指向
                                                                 特定槽位（如 target_order_slot: order_id_1）。
```

### 规划器三大分解核心模式

#### 1. 复合意图任务分解 (Multi-intent plan)
当意图识别检测到用户的提问中蕴含多个属于任务型且不相同的可能意图 `possibleIntents`（如：用户说“我的订单123退款了吗？顺便帮我查一下那个鱼香肉丝的售价”）。只要意图数在 `MAX_STEPS`（设为 3）限制内，便会自动划分为各意图对应的 `TaskStep`。每个步骤绑定了实体参数和专属描述，由编排器按顺序驱动执行。

#### 2. 批量取消计划 (Batch cancel plan)
专门针对退款/取消等高频批量痛点。如果判定意图为 `CANCEL_ORDER`，且提取出大于或等于 2 个的显式订单 ID，将自动编排同样数目的 `CANCEL_ORDER` 步骤，在后台顺序并发逐一取消目标订单。

#### 3. 检索驱动型多步取消分解 (Lookup-driven cancel plan)
这是系统中最具技术含量与智能化体现的模式。如果用户发出类似“帮我把最近 2 个还没送到的订单退了”这样没有携带任何明确订单号、但带有明显检索修饰词的命令，规划器会自动生成如下多步骤编排计划：
*   **Step 1**：意图为 `ORDER_STATUS`。它在 `entities` 中自动构造了 `query_mode = "recent_orders"`, `order_count = 2`, `order_status = "not_delivered"`。指示 LLM：
    > “请先查询最近的 2 个符合条件的订单，只返回 JSON：`{"order_ids":"id1,id2"}`。”
*   **Step 2 & Step 3**：意图均设为 `CANCEL_ORDER`。但是，其执行参数并不直接绑定明确的 ID，而是指向**动态插槽占位符**：`target_order_slot = "order_id_1"` 以及 `target_order_slot = "order_id_2"`。
*   **槽位级联绑定与动态状态注入**：当 Step 1 的 LLM 执行并调用订单服务返回真实的订单列表 JSON 后，编排器 `TaskOrchestratorService` 具备特异性的解析器，将直接抓取并解析提取出真实的订单 ID，并**动态对齐注入**到后续步骤的插槽占位符中，从而优雅地实现前置检索与后置操作之间的动态数据闭环和级联绑定。

---

## 5. 人工确认与高风险交互卡点 (Human Confirmation Box)

为了防止 AI 代理发生越权滥用或在未经用户审核的情况下执行敏感的资金或配送变更操作，系统在面临高风险意图时构建了**人工干预交互卡点**：

```
                [Advisor 链] / [分步任务规划器运行]
                            │
                            ├─► 判定为高风险意图且 requiresHumanConfirmation == true
                            ▼
                ┌──────────────────────────────────┐
                │        服务端执行拦截卡点        │
                │ 1. 挂起当前回合，停止 LLM 执行   │
                │ 2. 将原始问题、意图等存入 Session│
                └──────────────┬───────────────────┘
                               │
                               │ 推送 AgentChatConfirmationFrame 确认帧
                               ▼
                ┌──────────────────────────────────┐
                │          前端用户操作界面        │
                │ (弹出拟人化的确认弹窗询问)       │
                └──────────────┬───────────────────┘
                               │
                               ├─► 用户在 UI 界面手动点击“确认操作”
                               ▼
                ┌──────────────────────────────────┐
                │          WebSocket 确认帧        │
                │  (发送带高置信度 confirmedIntent) │
                └──────────────┬───────────────────┘
                               │
                               ▼
                ┌──────────────────────────────────┐
                │        二度驱动 / 恢复执行       │
                │ (复用原实体参数，零冗余重入执行) │
                └──────────────────────────────────┘
```

1. **状态拦截**：在任何 Advisor 运行期间，或者分步计划遇到高风险步骤前，一旦判定 `requiresHumanConfirmation`（人工确认）为 true，服务端会中断当前回合，将原始问题等状态存入 WebSocket Session 中（`pendingQuestion`, `pendingIntent`）。
2. **确认帧推送**：服务端以 `confirmation` 控制帧形式向前端发包。如果存在模型生成的 `humanConfirmationReason`（如“您有历史订单异常，此次取消可能影响权益，确认取消吗？”），则优先作为确认提示句。如果不存在，则基于本地模板兜底构建（如“是否确认取消订单 123456？”），并附带订单号与具体意图发回客户端。
3. **闭环恢复执行**：前端弹窗展示，用户点击确认后以 WebSocket 回发确认帧。服务端检测到匹配的确认请求，会将 `Session` 内的暂存数据复原。如果是复杂编排任务，则触发 `taskOrchestratorService.continueAfterConfirmation()` 恢复执行流程；如果是普通流式请求，则将其转化为 `confirmedIntent`（信心度设为高，并完整复用之前的实体），重新输入给 Advisor 链无障碍地执行，实现完美的安全卡点。

---

## 6. 三层记忆深度持久化系统与混合提取算法

Sky-AI 采用了业内先进的三层记忆架构，以确保持久化数据的高一致性与低延时响应。

### 3.1 三层内存架构

| 内存层级 | 承载组件与工作原理 | 存储媒介 | 作用范畴 |
| :--- | :--- | :--- | :--- |
| **工作记忆 (Working)** | 存储在当前的 `advisorContext`（基于 Java 内存 Map）。 | 局部线程内存 | 当前请求的生命周期 |
| **会话记忆 (Session)** | 基于 `RedisChatMemoryRepository` 实现。由 Spring AI 内部组件动态读取；限制最多存储最近 **20 条** 对话记录；**TTL 为 2 小时**，每次执行 `saveAll` 对话均会自动刷新 TTL 2 小时以保活。 | Redis (Jackson JSON 序列化) | 跨页面刷新或短时断开，保持对话上下文不丢失。 |
| **长期记忆 (Long-term)** | 映射为 `UserMemory` JPA 实体（对应 `user_memory_facts` 数据库表）。包含字段：`userId`, `dietaryPrefs`（饮食偏好）, `defaultAddress`（默认地址）, `knownIssues`（已知问题/操作备注, 长度 500）。 | PostgreSQL 关系库 | 跨会话的永久级用户行为模式与事实存储。 |

---

### 3.2 异步长期记忆提取与本地工具强一致持久化算法

所有关于长期记忆事实的持久化与提取流程均在对话回合结束后，由 `MemoryWriterService` 内的 `writeTurn` 方法异步进行（方法标注了 `@Async`，执行在独立线程池中，即使模型提取失败或写入库发生延迟，也**绝对不会阻塞 REST 响应或 WebSocket 消息的发送**，对用户无任何感知）。

长期记忆系统采用 **LLM 异步事实提取** 与 **本地代码强一致性更新** 的混合算法：

#### A. LLM 异步自适应提取事实与修正/遗忘机制
如果当前对话回合不包含需要人工确认的未竟卡点，且意图非 `LOW` 置信度的 `FAQ` / `OTHER`。长期记忆处理器将当前回合的 `USER` 语句拼接为文本副本传入 LLM，并使用 `EXTRACTION_SYSTEM_PROMPT` 提示词模板指挥模型提取最新的稳定用户事实：
- **修正机制 (Corrections)**：如果 LLM 在提取 JSON 中标有 `corrections`（修正列表），系统在调用 `UserMemoryFactService.upsertFact` 时会标记为 corrections 状态，这会在数据库中自动覆盖用户原先的事实，防止用户前后言行不一致导致记忆混乱。
- **主动遗忘机制 (Delete)**：如果提取出的事实值（如 `favorite_flavors`）的 `value == null`，表明用户做出了类似“我再也不爱吃清淡了，把这个抹去吧”的表述，系统底层会特异性触发 `deleteFact` 操作，在 PostgreSQL 中**将此条记忆事实物理删除**。

#### B. 非 LLM 依赖的强一致性本地工具持久化流程 (Tool Outcomes Auto-Persistence)
依靠大语言模型从非结构化的对话文本中读取订单号、退款金额等关键信息不仅响应缓慢，而且极易发生精度偏离（ hallucination 幻觉）。为此，Sky-AI 实现了**强一致性工具响应解析器**：
系统在启动 LLM 记忆分析前，会在 `persistToolOutcomes` 中率先对当前的对话消息链进行一遍全扫描，特异性提取所有的工具响应消息 `ToolResponseMessage`，只要工具执行返回值不包含失败头 `"FAIL:"`，就会针对特定高风险工具的成功执行自动执行精准解析与保存：
- **`ADDRESS_MANAGEMENT`**：自动解析响应的 JSON 实体，若包含非空 `detail` 地址字段，系统会以 `MemoryFactSourceType.TOOL`（工具来源）自动将 `DEFAULT_ADDRESS`（默认地址）事实 upsert 入 PostgreSQL 中。
- **`CHANGE_ADDRESS`**：提取响应文本中冒号分隔后的新地址部分（利用 `extractTail` 精准切分数据），自动覆盖入 `DEFAULT_ADDRESS` 长期记忆。
- **`CANCEL_ORDER`**：解析成功的响应并截获其中的 `orderId`。直接以 `MemoryFactSourceType.TOOL` 向用户的 `OPERATIONAL_NOTES`（操作记录）中以历史客观陈述风格**追加写入一条事实**：
  > `"已取消订单 {orderId}（{当前系统时间}）"`
- **`REQUEST_REFUND`**：自动解析成功的响应得到订单 ID 与退款原因。以历史陈述风格向 `OPERATIONAL_NOTES` 中**追加写入事实**：
  > `"已为订单 {orderId} 退款：{reason}"`

这种硬编码的工具响应解析器在很大程度上保证了系统的关键订单事实、退款事实具有 **100% 的准确度与一致性**，与 LLM 的自适应语义事实提取相得益彰，共同撑起了 Sky-AI 高可信、多维度的客服记忆大厦。

---

这就是 Sky-AI 智能客服 Agent 的整体设计与技术脉络。其以 Advisor 链为依托，借助分步任务编排和强韧的安全阻断与强一致记忆，构建了现代化大模型应用极其珍贵的商业化生产级闭环。
