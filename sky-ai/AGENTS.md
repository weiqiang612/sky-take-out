# Memory, tool calling, and MCP — food delivery customer service agent

## Project context

Spring AI–based food delivery customer service agent. Two modules already exist and must not
be modified: the RAG module and the intent recognition module. You are implementing the memory
module that integrates with both.

**Existing enums — use these values exactly, do not redefine them:**

```java
public enum IntentType {
    ORDER_STATUS("order_status"),
    CANCEL_ORDER("cancel_order"),
    REQUEST_REFUND("request_refund"),
    TRACK_DELIVERY("track_delivery"),
    REPORT_MISSING_ITEM("report_missing_item"),
    CHANGE_ADDRESS("change_address"),
    FAQ("faq"),
    ESCALATE_TO_HUMAN("escalate_to_human"),
    OTHER("other");
}

public enum ConfidenceLevel {
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");
}
```

**Existing record — read-only, do not modify:**

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntentRecognitionResult(
        @JsonProperty("intent") IntentType intent,
        @JsonProperty("confidence") ConfidenceLevel confidence,
        @JsonProperty("entities") Map<String, String> entities,
        @JsonProperty("possible_intents") List<IntentType> possibleIntents,
        @JsonProperty("clarification_question") @Nullable String clarificationQuestion,
        @JsonProperty("requires_human_confirmation") boolean requiresHumanConfirmation,
        @JsonProperty("human_confirmation_reason") @Nullable String humanConfirmationReason
) {}
```

**Three-layer memory architecture:**

| Layer | Implementation | Storage | Scope |
|---|---|---|---|
| Working | `MessageChatMemoryAdvisor` | In-context | Current turn (last 20 msgs) |
| Session | `RedisChatMemoryRepository` | Redis TTL 2h | Cross-reload, same user |
| Long-term | `UserMemory` JPA entity | PostgreSQL | Persistent across sessions |

**Fixed advisor chain order:**

```
IntentRecognitionAdvisor
        ↓  (writes "intentResult" to advisorContext)
UserContextAdvisor
        ↓  (reads "intentResult", injects memory + sets "allowedTools")
MessageChatMemoryAdvisor
        ↓
QuestionAnswerAdvisor   ← existing RAG module, do not modify
        ↓
ToolFilterAdvisor       ← reads "allowedTools", scopes tool visibility
        ↓
ChatClient
```

`advisorContext` is the shared mutable map (`Map<String, Object>`) passed through the chain.

---

## Coding rules

### 1. Think before coding

Before writing any code for a task, output a short reasoning block:

- State your interpretation of the task. If ambiguous, list interpretations and stop — do not
  pick one silently.
- List assumptions about existing code or interfaces that you cannot verify from files in the
  repo. Flag each one explicitly.
- If a simpler solution exists, name it. Recommend it even if it reduces scope.
- If anything is unclear, output: `BLOCKED: <what is unclear>` and stop.

### 2. Simplicity first

Produce the minimum code that satisfies the requirement. Then stop.

- No additional methods, fields, or classes beyond what the task requires.
- No abstraction for logic used in only one place.
- No optional configurability that was not requested.
- No defensive error handling for states that the existing code makes impossible.
- Hard limit: if a class exceeds 80 lines, justify each block or shorten it before committing.

### 3. Surgical changes

- Edit only the files the task requires.
- Do not reformat, rename, or reorganise anything outside the lines you are changing.
- Match existing code style in every file: indentation, naming, annotation style, import order.
- If you introduce a new import, variable, or method that your own changes then make unused,
  remove it. Do not remove pre-existing unused code.
- If you spot unrelated dead code, note it in your reasoning block. Do not touch it.

### 4. Goal-driven execution

For any task with more than one step, begin with a plan:

```
1. [action] → verify: [concrete check]
2. [action] → verify: [concrete check]
```

Verification must be runnable. Prefer: "run `./mvnw test -pl memory -Dtest=ClassName`
and confirm zero failures" over "check it works". Do not advance to the next step until
the current step's verify condition passes.

---

## Environment

- Build tool: Maven (`./mvnw`). Do not use Gradle commands.
- Java 17. Use records, sealed interfaces, and switch expressions where appropriate.
- Run tests with: `./mvnw test -pl <module> -Dtest=<TestClass>`
- Run all tests: `./mvnw verify`
- Do not start the application to verify behaviour — use unit or integration tests only.
- Redis and PostgreSQL are available as Docker services. Connection details are in
  `src/test/resources/application-test.yml`. Use `@SpringBootTest` with the `test` profile
  for integration tests.
- Do not commit secrets. Use environment variable placeholders: `${REDIS_HOST}`,
  `${DB_PASSWORD}`, etc.

---

## Memory module — design constraints

### `IntentRecognitionAdvisor` (implements `CallAroundAdvisor`)

- Before classification, fetch from Redis: last 4 messages for the current `conversationId`.
- Before classification, fetch from `UserMemory`: `knownIssues` summary for the current
  `userId` (one sentence max when injected into the prompt).
- Pre-populate known entities into the classification prompt. If session memory contains an
  active `order_id`, include it so the LLM confirms rather than re-extracts.
- After classification, write `IntentRecognitionResult` into `advisorContext` under key
  `"intentResult"`.
- When `confidence == LOW`: pass only the raw user message to the classifier — no session
  context, no user history. The clarification question in the result handles disambiguation.
- When `confidence` is `MEDIUM` or `HIGH`: include session context and user history summary.

### `UserContextAdvisor` (implements `CallAroundAdvisor`)

- Read `advisorContext.get("intentResult")`. If null, treat as `OTHER` / `LOW`.
- Switch on `IntentType`. Fetch only what is listed below — nothing more:

  | Intent | Fetch |
  |---|---|
  | `ORDER_STATUS`, `TRACK_DELIVERY` | Order by `entities.get("order_id")`; fall back to last 3 orders |
  | `CANCEL_ORDER`, `REQUEST_REFUND` | Order by `entities.get("order_id")` (required); `knownIssues` from `UserMemory` |
  | `REPORT_MISSING_ITEM` | Order by `entities.get("order_id")`; item list from that order |
  | `CHANGE_ADDRESS` | `defaultAddress` from `UserMemory` only |
  | `ESCALATE_TO_HUMAN` | Last 3 orders + full `knownIssues` block |
  | `FAQ` | `dietaryPrefs` from `UserMemory` only |
  | `OTHER` + `LOW` | Nothing — return empty string |
  | `OTHER` + `MEDIUM` or `HIGH` | `defaultAddress` from `UserMemory` only |

- Prepend the assembled block to the system prompt via `AdvisedRequest.from(request).systemText(...)`.
- Context block hard limit: 5 sentences. Summarise — do not dump raw JSON or field lists.

### `RedisChatMemoryRepository` (implements `ChatMemoryRepository`)

- Key: `chat:{conversationId}` (string).
- Value: JSON array of `Message` objects, serialised with Jackson.
- TTL: 2 hours. Reset the TTL on every `saveAll` call.
- `findByConversationId` returns an empty list (not null) for unknown keys.

### Memory writer

- Class annotated `@Service`. Method annotated `@Async`.
- Called after every turn from outside the advisor chain (do not embed inside an advisor).
- Guard condition — check `requiresHumanConfirmation` first:
  - `true` → write the updated message list to Redis only. Return immediately.
  - `false` → call LLM extraction prompt, parse result, merge into `UserMemory`, save.
- LLM extraction prompt must instruct the model to return JSON only (no markdown, no
  preamble). Strip any ` ```json ` fences before parsing as a safety measure.
- Skip the long-term write entirely if the session contained only `FAQ` or `OTHER` intents
  at `LOW` confidence. Log the skip at `DEBUG` level.
- Log extracted facts at `DEBUG` level. Do not log PII at `INFO` or above.

### `UserMemory` (JPA entity)

Required fields only — do not add columns without being asked:

```
userId          String   @Column(unique = true, nullable = false)
dietaryPrefs    String   @Column(nullable = true)
defaultAddress  String   @Column(nullable = true)
knownIssues     String   @Column(nullable = true)   // free-text summary, max 500 chars
updatedAt       Instant  @Column(nullable = false)
```

Use `@Column(length = 500)` on `knownIssues`. No other length constraints unless specified.

### What NOT to build

Do not build any of the following unless explicitly asked in a follow-up task:

- A cache in front of PostgreSQL reads.
- An audit log or event-sourced history for memory changes.
- Admin or debug HTTP endpoints for memory inspection or clearing.
- Versioned preference history.
- Sub-type classification of `FAQ` or `OTHER` intents in Java code.
- A custom `IntentType` wrapper or adapter — use the enum directly in switch expressions.

---

## Tool calling and MCP — design constraints

### `OrderTools` (`@Tool` methods)

Single `@Component`. Expose exactly these three tools:

| Method | Arguments | Delegates to |
|---|---|---|
| `cancelOrder` | `String orderId` | `OrderService.cancel(orderId)` |
| `requestRefund` | `String orderId, String reason` | `RefundService.issue(orderId, reason)` |
| `updateDeliveryAddress` | `String orderId, String newAddress` | `OrderService.updateAddress(orderId, newAddress)` |

Each method annotated `@Tool(description = "...")`. Description must be one sentence,
specific enough that the LLM can select the correct tool without ambiguity. Do not add
parameter validation beyond null-checks.

Register on `ChatClient` via `.defaultTools(orderTools)`. Not inline, not as lambdas.

### `ToolFilterAdvisor` (implements `CallAroundAdvisor`)

Sits immediately before `ChatClient`. Reads `allowedTools` (`Set<String>`) from
`advisorContext`. If absent, passes an empty set — the LLM sees no tools.

Intent-to-tools mapping (set by `UserContextAdvisor`, read here):

```
CANCEL_ORDER        → {"cancelOrder"}
REQUEST_REFUND      → {"requestRefund"}
CHANGE_ADDRESS      → {"updateDeliveryAddress"}
all other intents   → {} (empty)
```

`ToolFilterAdvisor` must contain no intent logic — it only reads `allowedTools` and
applies it. Intent routing lives in `UserContextAdvisor`.

### MCP server integration

Declare in `application.yml` only. No Java configuration required:

```yaml
spring:
  ai:
    mcp:
      client:
        servers:
          maps-server:
            url: ${MCP_MAPS_URL}
          payments-server:
            url: ${MCP_PAYMENTS_URL}
          notifications-server:
            url: ${MCP_NOTIFICATIONS_URL}
```

Spring AI auto-discovers tool schemas at startup. MCP tools behave identically to local
`@Tool` methods from `ChatClient`'s perspective.

Add MCP tool names to the `allowedTools` sets in `UserContextAdvisor` if they need
intent-gating. `FAQ` and `OTHER` must never expose payment, cancellation, or notification
tools.

All MCP URLs must use environment variable placeholders — no hardcoded hostnames.

### Memory writer — tool outcome persistence

After the existing `requiresHumanConfirmation` gate, scan messages for
`ToolResponseMessage` instances and call `persistToolOutcome` for each:

| Intent | Persistence action |
|---|---|
| `CANCEL_ORDER` | Append `"Cancelled order {id} on {date}"` to `knownIssues` |
| `REQUEST_REFUND` | Append `"Refund issued for order {id}: {reason}"` to `knownIssues` |
| `CHANGE_ADDRESS` | Overwrite `defaultAddress` directly — no LLM extraction |

`persistToolOutcome` must be private and under 20 lines. Do not call the LLM for tool
outcome persistence — the tool response string contains all required facts.

Skip persistence if the tool response string indicates a failure. Ask what the standard
error strings from `OrderService` and `RefundService` look like before implementing if
they are not visible in the repo.

### Verification steps for new modules

```
1. Implement OrderTools         → verify: unit test confirms each @Tool method delegates
                                           to the correct service and returns a string
2. Implement ToolFilterAdvisor  → verify: unit test confirms empty set = no tools in
                                           AdvisedRequest; correct set = tool names present
3. Wire MCP in application.yml  → verify: integration test confirms tool names from each
                                           MCP server appear in ChatClient tool registry
4. Update memory writer         → verify: unit test with a mock ToolResponseMessage confirms
                                           knownIssues / defaultAddress updated correctly;
                                           failed tool responses produce no write
```

### What NOT to build unless explicitly asked

- No retry logic around tool calls — Spring AI handles this at the framework level.
- No tool call audit log separate from the existing memory writer.
- No MCP tool schema validation in Java — trust the server's schema at startup.
- No fallback tool implementations for when an MCP server is unavailable.
