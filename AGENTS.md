# Repository Guidelines

## Project Structure & Module Organization
This repository is a multi-module Maven project. The root `pom.xml` aggregates:
- `sky-common`: shared constants, exceptions, results, context, and utility classes.
- `sky-pojo`: DTO, entity, and VO definitions shared across layers.
- `sky-server`: the Spring Boot application with controllers, services, mappers, AOP, config, handlers, scheduled jobs, WebSocket support, and resources.
- `sky-ai`: AI-related module aggregated by the root Maven build.

Main source code lives under `src/main/java/com/sky`. SQL mappings are in `sky-server/src/main/resources/mapper`, and templates/assets are under `sky-server/src/main/resources/template`.

## Build, Test, and Development Commands
- `mvn clean test`: build all modules and run the test suite.
- `mvn clean package -DskipTests`: create a packaged artifact without running tests.
- `mvn -pl sky-server spring-boot:run`: start the backend locally from the `sky-server` module.
- `mvn -pl sky-server test`: run only the server module tests.
- `mvn -pl sky-ai test`: run only the AI module tests.

## Coding Style & Naming Conventions
Use standard Java conventions: `PascalCase` for classes, `camelCase` for methods and fields, and lowercase package names under `com.sky`. Keep code in the existing layer boundaries: controller, service, mapper, config, handler, interceptor, task, and websocket. Follow the surrounding style for Lombok, Spring annotations, and inline comments. No repository-wide formatter, Checkstyle, Spotless, or Jacoco configuration is declared, so match the local code style carefully.

## Testing Guidelines
Tests currently live in `sky-server/src/test/java/com/sky/test` and use JUnit 5 through `spring-boot-starter-test`. Prefer focused integration-style tests for controller, Redis, HTTP client, and file/export behavior. Name test classes `*Test` and keep any temporary test data outside tracked resources.

## Commit & Pull Request Guidelines
Recent commits use concise prefixes such as `feature:` and `refactor:` followed by a short description. Keep commit messages similarly specific and imperative. Pull requests should summarize the change, explain how it was verified, and call out any configuration or API impact. Include screenshots or sample payloads only when they help demonstrate behavior.

## Security & Configuration Tips
Keep environment-specific values in `sky-server/src/main/resources/application-dev.yml` or local overrides. Do not commit secrets, credentials, or machine-specific paths. Review changes to Redis, OSS, database, and payment configuration carefully before merging.
