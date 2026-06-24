# AGENTS.md — sky-server

> Supplements the root `AGENTS.md`. All root rules apply unless explicitly overridden.

## Module context
- **Purpose**: Core Spring Boot 2.7.3 backend service handling restaurant orders, employee management, shopping carts, addresses, and WeChat Pay integration.
- **Port**: 8080
- **Entry point**: `com.sky.SkyApplication`

## Commands
- **Run**: `mvn spring-boot:run -pl sky-server -Dspring-boot.run.profiles=dev` (run from `sky-take-out/`)
- **Test**: `mvn test -pl sky-server` (run from `sky-take-out/`)

## Module-specific constraints
- 🚫 **MUST NOT** bypass `@AutoFill` aspect annotations for audit fields in Mapper interfaces.
- 🚫 **MUST NOT** import Mapper or Repository interfaces directly in Controllers — always delegate through the Service layer.
- 🚫 **MUST NOT** bypass thread-local JWT user context (`BaseContext.getCurrentId()`) inside HTTP requests requiring authorization.
- ⚠️ **ASK FIRST** before introducing new Spring Configuration beans or altering existing global exception handlers (`GlobalExceptionHandler`).
- ✅ **ALWAYS** use parameterised log messages (`log.info("msg: {}", value)`) and hide sensitive PII at INFO level.

## Documentation
- Standards: root `docs/1-standards/README.md`
- Constraints: root `docs/3-constraints/`
- Tasks: root `docs/4-tasks/`
