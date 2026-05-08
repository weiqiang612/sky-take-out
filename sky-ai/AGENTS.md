# Repository Guidelines

## Project Structure & Module Organization
This repository is a single Spring Boot application built with Maven. Main application code lives in `src/main/java/com/weiqiang/skyai`, with controllers, services, config, and RAG-related code split into subpackages such as `controller`, `service`, `config`, and `rag/offline`. Test code lives in `src/test/java/com/weiqiang/skyai`, and sample or local-only assets are kept under `src/main/resources` alongside environment files like `application.yml`, `application-dev.yml`, `application-test.yml`, and `application-local.example.yml`.

## Build, Test, and Development Commands
- `mvn clean test` - compile the project and run the full test suite.
- `mvn clean package -DskipTests` - produce the application JAR without running tests.
- `mvn spring-boot:run` - start the application locally from the project root.
- `mvn test -Dtest=OfflineIndexServiceTests` - run a focused test class when iterating on one component.

## Coding Style & Naming Conventions
Use Java 17 and standard Spring conventions. Prefer `PascalCase` for classes, `camelCase` for methods and fields, and lowercase package names under `com.weiqiang.skyai`. Keep classes in the existing package boundaries instead of introducing new top-level layers. Lombok is enabled, so follow the surrounding style for annotations such as `@RequiredArgsConstructor` and `@Slf4j`. No repository-wide formatter or linter is configured, so match nearby code formatting and import order.

## Testing Guidelines
Tests use JUnit 5 through `spring-boot-starter-test`. Name test classes with the `*Tests` suffix, for example `SkyAiApplicationTests` or `ChunkingStrategyTests`. Prefer focused integration-style tests for controllers, document parsing, indexing, and other RAG workflows. Keep temporary test fixtures outside tracked resources unless they are meant to be reused.

## Commit & Pull Request Guidelines
Git history uses short, imperative prefixes such as `feature:` and `feat:` followed by a brief description. Keep commits similarly specific and scoped. Pull requests should summarize the change, explain how it was verified, and note any config or API impact. Include sample payloads or screenshots only when they clarify behavior.

## Security & Configuration Tips
Do not commit secrets or machine-specific values. Store environment-specific settings in `src/main/resources/application-dev.yml` or a local override based on `application-local.example.yml`. Review changes to database, AI provider, and vector-store configuration carefully before merging.
