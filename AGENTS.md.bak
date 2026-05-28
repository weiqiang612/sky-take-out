# Repository Guidelines

## Project Structure & Module Organization
This repository is a multi-module Maven project. The root `pom.xml` aggregates:
- `sky-common`: shared constants, exceptions, results, context, and utility classes.
- `sky-pojo`: DTO, entity, and VO definitions shared across layers.
- `sky-server`: Spring Boot 2.7.3 application with controllers, services, mappers, AOP, config, handlers, scheduled jobs, WebSocket support, and resources. Organized under `src/main/java/com/sky` with subdirectories: `controller/`, `service/`, `mapper/`, `aspect/`, `config/`, `handler/`, `interceptor/`, `annotation/`, `task/`, `websocket/`.
- `sky-ai`: Separate Spring Boot 3.4.13 (Spring AI 1.1.5) module for AI-powered customer service agent, featuring intent recognition, memory management, RAG integration, MCP tool support, and Vector DB integration. See `sky-ai/AGENTS.md` for AI-specific coding rules.

**Version note:** `sky-ai` uses Java 17 and Spring Boot 3, while `sky-server` uses Java 11+ and Spring Boot 2. They do not share dependencies directly; `sky-ai` is independently deployable and consumed via REST APIs.

Main source code lives under `src/main/java/com/sky` (sky-server) and `src/main/java/com/weiqiang/skyai` (sky-ai). SQL mappings are in `sky-server/src/main/resources/mapper`. AI-specific resources (RAG models, prompts) live in `sky-ai/src/main/resources`.

## Build, Test, and Development Commands
- `mvn clean test`: build all modules and run the test suite.
- `mvn clean package -DskipTests`: create a packaged artifact without running tests.
- `mvn -pl sky-server spring-boot:run`: start the backend locally from the `sky-server` module (port 8080).
- `mvn -pl sky-ai spring-boot:run`: start the AI module locally (requires OPENAI_API_KEY or OLLAMA_BASE_URL env var; see `sky-ai/src/main/resources/application-dev.yml` for Redis/PostgreSQL config).
- `mvn -pl sky-server test`: run only the server module tests.
- `mvn -pl sky-ai test`: run only the AI module tests (uses testcontainers for Redis/PostgreSQL).
- `mvn -pl sky-ai verify`: run all AI tests including Spring Boot integration tests.

**Profile & configuration:**
- Dev environment: activate `dev` profile via `spring.profiles.active: dev` or `SPRING_PROFILES_ACTIVE=dev`.
- Config overrides: per-module `application-{profile}.yml` in `src/main/resources`; environment variables for secrets (e.g., `OPENAI_API_KEY`, `REDIS_HOST`).
- Sky-server connects to MySQL, Redis, and Aliyun OSS (configured in `application-dev.yml`).
- Sky-ai connects to Redis and PostgreSQL; requires LLM endpoint (OpenAI or local Ollama).

## Coding Style & Naming Conventions
Use standard Java conventions: `PascalCase` for classes, `camelCase` for methods and fields, and lowercase package names.

**Sky-server (Spring Boot 2.7, Java 11+):**
- Keep code in the existing layer boundaries: `controller/`, `service/`, `mapper/`, `config/`, `handler/`, `interceptor/`, `annotation/`, `task/`, `websocket/`.
- Controllers: use `@RestController`, `@RequestMapping`, `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`. Constructor-inject service dependencies. Example: `public ControllerName(Service1 service1, Service2 service2) { this.service1 = service1; ... }`.
- Services: implement business logic; use `@Service`. Injected via constructor. May delegate to mapper or external APIs (Aliyun OSS, WeChat Pay).
- Mappers: `@Mapper` interfaces annotated with MyBatis; single-line method signatures. Use `@AutoFill` for audit fields (created_time, updated_time, created_user, updated_user).
- Context management: `BaseContext.setCurrentId(userId)` / `BaseContext.getCurrentId()` stores thread-local user/employee ID from JWT claims. Accessed within same request.
- Exceptions: throw `BaseException` for business errors; `GlobalExceptionHandler` catches and returns `Result.error(message)`.
- Follow existing style for Lombok annotations (`@Slf4j`, `@Builder`, `@Data`), Spring annotations, and inline comments. No repository-wide formatter.

**Sky-ai (Spring Boot 3.4, Java 17):**
- Use Java 17 features: records, sealed interfaces, switch expressions, text blocks.
- Constructor injection standard (same as sky-server).
- AI-specific layers: `advisor/` (Spring AI callchain), `controller/`, `service/`, `memory/`, `intent_recognition/`, `RAG/`, `tools/`, `websocket/`.
- Advisors implement `CallAroundAdvisor` interface. Pass `advisorContext` (shared `Map<String, Object>`) through the chain.
- See `sky-ai/AGENTS.md` for AI-specific coding rules (memory persistence, tool calling, MCP integration).

## Testing Guidelines

**Sky-server:**
- Tests live in `sky-server/src/test/java/com/sky/test` and use JUnit 5 through `spring-boot-starter-test`.
- Prefer focused integration-style tests for controller, Redis, HTTP client, and file/export behavior.
- Name test classes `*Test`.
- Keep temporary test data outside tracked resources.

**Sky-ai:**
- Tests live in `sky-ai/src/test/java/com/weiqiang/skyai` and use JUnit 5 through `spring-boot-starter-test`.
- Use `@SpringBootTest` with test profile (`application-test.yml`) for integration tests.
- Testcontainers auto-provision Redis and PostgreSQL for CI/test environments.
- Test advisors, memory repositories, tool calling behavior, and intent recognition separately.
- See `sky-ai/AGENTS.md` verification steps for testing patterns per module (OrderTools, ToolFilterAdvisor, MCP integration, memory writer).

## Commit & Pull Request Guidelines
Recent commits use concise prefixes such as `feature:` and `refactor:` followed by a short description. Keep commit messages similarly specific and imperative. Pull requests should summarize the change, explain how it was verified, and call out any configuration or API impact. Include screenshots or sample payloads only when they help demonstrate behavior.

## AI Integration (Sky-AI Module)
The `sky-ai` module implements a Spring AI–based customer service agent with the following architecture:

- **Intent recognition:** Classifies customer queries (order status, cancel order, request refund, track delivery, etc.) using LLM + context.
- **Memory layers:** Working (in-context, last 20 messages), Session (Redis TTL 2h), Long-term (PostgreSQL JPA entity).
- **Advisor chain:** `IntentRecognitionAdvisor` → `UserContextAdvisor` → `MessageChatMemoryAdvisor` → `QuestionAnswerAdvisor` (RAG) → `ToolFilterAdvisor` → `ChatClient`.
- **Tool calling:** Local `@Tool` methods (cancelOrder, requestRefund, updateDeliveryAddress) + MCP servers (maps, payments, notifications).
- **API endpoint:** `/api/chat` on sky-ai (port varies by deployment) receives user messages, returns LLM responses with optional tool outcomes.

**Key integration points:**
- Sky-server exposes AI customer API endpoints under `/ai/customer/**` (e.g., `GET /ai/customer/orders/{id}`, `PUT /ai/customer/orders/{id}/cancel`). Sky-ai calls these via HTTP (not direct dependency).
- MCP servers configured in `sky-ai/application.yml` via `spring.ai.mcp.client.servers.*` with environment variable URLs.
- Memory persistence is async; does not block API response.
- See `sky-ai/AGENTS.md` for detailed coding rules and verification steps.

## Security & Configuration Tips

**Configuration management:**
- Keep environment-specific values in module-level `src/main/resources/application-{profile}.yml` (e.g., `application-dev.yml`).
- Use environment variable placeholders for secrets: `${MYSQL_PASSWORD}`, `${OPENAI_API_KEY}`, `${REDIS_HOST}`, etc. Never commit actual credentials.
- Activate profiles via `spring.profiles.active` in `application.yml` or `SPRING_PROFILES_ACTIVE` env var.

**Authentication & JWT:**
- Sky-server: JWT interceptors (`JwtTokenAdminInterceptor`, `JwtTokenUserInterceptor`) extract token from header, parse claims, store ID in `BaseContext` (ThreadLocal).
- JWT secrets and TTLs configured per role (admin/user) in `sky.jwt.*` properties.
- Sky-ai: AI module does not validate JWT directly; receives user context via `X-AI-User-Id` header from client (or injected via advisor).

**External integrations:**
- Review changes to Redis, OSS, database, and payment configuration carefully before merging.
- Aliyun OSS: credentials via `sky.alioss.*` properties; SDK handles signed URLs internally.
- WeChat Pay: signatures verified in `PayNotifyController`; production requires valid merchant credentials.
- MCP servers: all URLs must use env var placeholders; test in CI with Docker services before production deploy.

**Data sensitivity:**
- Do not log PII (user ID, email, password, card number) at INFO level or above; use DEBUG if necessary with explicit PII masking.
- Database and Redis passwords must be environment variables, never committed.
- API keys (OpenAI, Ollama, MCP server credentials) must be environment variables.
