# Tasks 001: Balance Gateway

| | |
|---|---|
| Spec | [spec.md](spec.md) |
| Plan | [plan.md](plan.md) |

Rules: tasks are done in order. A task is checked only when `./mvnw verify` passes. Each stage ends with requests against the running services, a `spec-reviewer` run for the stories it covers, and one commit.

Legend: **Covers** lists the acceptance criteria (spec IDs) or plan sections a task implements. **Verify** says how completion is checked.

## Stage 0: Scaffold

Commit: `build: scaffold multi-module build, compose and CI`

- [ ] **T0.1** Parent `pom.xml` (parent `spring-boot-starter-parent` 3.5.16, Java 21, modules, pinned versions for Resilience4j, springdoc and WireMock, `-Xlint:all -Werror`, Surefire for `*Test`, Failsafe for `*IT` bound to `integration-test` and `verify`).
  Covers: NFR-4. Verify: `./mvnw -q validate`.
- [ ] **T0.2** Maven Wrapper pinned to Maven 3.9.16 (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`), executable bit set in git.
  Covers: NFR-4. Verify: `./mvnw -v` prints 3.9.16.
- [ ] **T0.3** Modules `auth-server`, `balance-api`, `core-mock`, each with its main class, `application.yml` with `local` and `docker` profiles and a port, and an application-context test.
  Covers: plan "Modules". Verify: `./mvnw verify` runs one test per module.
- [ ] **T0.4** One multi-stage `Dockerfile` per module (JDK Alpine build with BuildKit cache, JRE Alpine runtime, extracted layered jar, non-root user) and a `docker-compose.yml` with the three services.
  Covers: NFR-2 (non-root), NFR-4. Verify: `docker compose build` succeeds; `docker compose up` starts three containers.
- [ ] **T0.5** `.env.example`, `LICENSE` (MIT), README skeleton (purpose, CI badge, module table, links to spec, plan and ADRs).
  Covers: NFR-2 (secrets), NFR-4. Verify: links resolve.
- [ ] **T0.6** `.github/workflows/ci.yml`: `./mvnw -B verify` on every push and on pull requests to `main`, Temurin 21, Maven dependency cache.
  Covers: NFR-4, SC-2. Verify: workflow reviewed against the GitHub Actions documentation; it runs on the first push of the branch by the maintainer.
- [ ] **T0.7** Spec status set to "Approved".
  Verify: `spec.md` header.

## Stage 1: core-mock with chaos controls

Commit: `feat(core-mock): add core balance simulator with runtime chaos controls`

- [ ] **T1.1** `GET /core/accounts/{accountId}/balance` with fixed data for `ACC-1001`..`ACC-1004` (`asOf` = time of read), `404` for unknown accounts.
  Covers: plan "core-mock". Verify: `CoreBalanceControllerTest`; `curl` against the running service.
- [ ] **T1.2** `ChaosSettings` record held in an `AtomicReference`; `GET`, `POST` and `DELETE /admin/chaos` with validation (`latencyMs >= 0`, `0 <= errorRatePercent <= 100`).
  Covers: US6-AC1, US6-AC2. Verify: `ChaosControllerTest`.
- [ ] **T1.3** Chaos applied to balance requests: outage returns `503`, then latency, then random `500` by error rate. Randomness injected so tests are deterministic.
  Covers: US6-AC1. Verify: `CoreBalanceControllerTest` (outage, forced error, latency).
- [ ] **T1.4** Log each balance request with the received `X-Correlation-Id` in the MDC; Actuator health exposed.
  Covers: US5-AC3 (core side). Verify: log line seen in `curl` session.
- [ ] **T1.5** `spec-reviewer` for US6.

## Stage 2: balance-api without security

Commit: `feat(balance-api): add balance endpoint backed by the core client`

- [ ] **T2.1** `CoreProperties` (`balance.core.base-url`, `connect-timeout`, `read-timeout`) and `CoreClientConfiguration` building a `RestClient` on the JDK `HttpClient` with those timeouts.
  Covers: plan "Resilience configuration" (timeouts), ADR-0001. Verify: `BalanceResilienceIT` smoke test against WireMock.
- [ ] **T2.2** `CoreBalanceClient`: GET the core balance, map `404` to `AccountNotFoundException`; records `AccountBalance` (domain) and `BalanceResponse` (API).
  Covers: US1-AC1, US1-AC2. Verify: `BalanceResilienceIT` (OK, 404).
- [ ] **T2.3** `BalanceController` `GET /v1/accounts/{accountId}/balance` with account id validation and `Cache-Control: no-store`; `BalanceService` passing through to the client.
  Covers: US1-AC1, US1-AC3. Verify: `BalanceControllerTest` (200, 400).
- [ ] **T2.4** `ApiExceptionHandler` (`@RestControllerAdvice` extending `ResponseEntityExceptionHandler`) producing Problem Details for 400, 404, 502 and 500; `spring.mvc.problemdetails.enabled`; no stack traces or exception messages in bodies.
  Covers: US1-AC2, US1-AC3, NFR-2, NFR-5. Verify: `BalanceControllerTest` asserts `application/problem+json` and fields.
- [ ] **T2.5** springdoc with Swagger UI at `/swagger-ui.html`, OpenAPI at `/v3/api-docs`, error responses documented on the operation.
  Covers: US7-AC1. Verify: `curl /v3/api-docs`; `ObservabilityIT` added in stage 5.
- [ ] **T2.6** Actuator on management port 8081 with `health`, `info`, `metrics`; liveness and readiness groups; virtual threads enabled.
  Covers: plan "Observability", plan "balance-api". Verify: `curl :8081/actuator/health/liveness`.
- [ ] **T2.7** ADR-0001 checked against the implementation.
- [ ] **T2.8** `spec-reviewer` for US1 and US7.

## Stage 3: Resilience

Commit: `feat(balance-api): add deadline, retry, circuit breaker and stale fallback`

- [ ] **T3.1** Resilience4j starter (no AOP: composition is programmatic); `resilience4j.retry`, `circuitbreaker` and `timelimiter` instances named `core` in `application.yml` with the plan's values.
  Covers: NFR-3, plan "Resilience configuration". Verify: `/actuator/circuitbreakers` lists `core` (after T3.6).
- [ ] **T3.2** `ResilientCoreBalanceClient` composing `TimeLimiter(Retry(CircuitBreaker(call)))` on a virtual-thread executor, copying the MDC, translating failures into `CoreUnavailableException`.
  Covers: US3-AC1..AC5, ADR-0002. Verify: `ResilientCoreBalanceClientTest` (deadline on hanging core; open circuit not retried).
- [ ] **T3.3** `LastKnownBalanceCache` (Caffeine, `balance.fallback.max-age`, `max-entries`, injectable ticker) and fallback decision in `BalanceService`; `BalanceUnavailableException` mapped to `503`.
  Covers: US4-AC1..AC3, ADR-0003. Verify: `BalanceServiceTest`, `LastKnownBalanceCacheTest`.
- [ ] **T3.4** `BalanceResilienceIT` with WireMock: retry on 5xx then success, retry on read timeout then success, no retry on 404, circuit opens on sustained 5xx and stops calling the core, half-open recovery, stale fallback with original `asOf`, `503` without cache, response within the deadline when the core hangs.
  Covers: US3-AC1..AC5, US4-AC1..AC3, NFR-1. Verify: `./mvnw verify`.
- [ ] **T3.5** `ResilienceEventLogger` logging retries and circuit breaker state transitions.
  Covers: US5 (demo: retries visible in logs). Verify: log lines during the stage demo.
- [ ] **T3.6** Expose `circuitbreakers`, `circuitbreakerevents`, `retries`, `retryevents`; circuit breaker health indicator registered with `allow-health-indicator-to-fail: false`.
  Covers: US5-AC1. Verify: `curl :8081/actuator/health` with the core down.
- [ ] **T3.7** ADR-0002 and ADR-0003 checked against the implementation.
- [ ] **T3.8** `spec-reviewer` for US3 and US4.

## Stage 4: auth-server and resource server

Commit: `feat: add authorization server and require balance.read scope`

- [ ] **T4.1** auth-server: `spring.security.oauth2.authorizationserver` issuer and clients `ivr-channel` (`balance.read`) and `reporting` (`reporting.read`), `client_credentials` only, secrets from environment variables, 5-minute token TTL.
  Covers: US2-AC4, ADR-0004. Verify: `AuthServerIT`; `curl` to `/oauth2/token` and `/oauth2/jwks`.
- [ ] **T4.2** balance-api: `issuer-uri` and `jwk-set-uri`; `SecurityConfiguration` (stateless, `SCOPE_balance.read` on `/v1/accounts/**`, OpenAPI public, everything else denied, Actuator permitted).
  Covers: US2-AC1..AC3. Verify: `BalanceControllerTest`.
- [ ] **T4.3** `ProblemDetailsAuthenticationEntryPoint` (keeps `WWW-Authenticate`) and `ProblemDetailsAccessDeniedHandler`.
  Covers: US2-AC1, US2-AC3, NFR-5. Verify: `BalanceControllerTest` asserts problem bodies and header.
- [ ] **T4.4** Slice tests with `spring-security-test` `jwt()`: no token 401, invalid token 401, without scope 403, with scope 200. Existing tests updated to send a token.
  Covers: US2-AC1..AC3. Verify: `./mvnw test`.
- [ ] **T4.5** `JwtValidationIT`: tokens signed with a test RSA key, JWKS served by WireMock; expired, wrong issuer and wrong key rejected with 401, valid token accepted.
  Covers: US2-AC2. Verify: `./mvnw verify`.
- [ ] **T4.6** OpenAPI security scheme (OAuth 2.0 client credentials) on the operation.
  Covers: US7-AC1. Verify: `curl /v3/api-docs`.
- [ ] **T4.7** ADR-0004 checked against the implementation.
- [ ] **T4.8** `spec-reviewer` for US2.

## Stage 5: Correlation id, structured logs, metrics

Commit: `feat(balance-api): add correlation id propagation, JSON logs and fallback metrics`

- [ ] **T5.1** `CorrelationIdFilter` (highest precedence): validate or generate, MDC, response header, MDC cleared after the request.
  Covers: US5-AC3, US5-AC4. Verify: `CorrelationIdFilterTest`.
- [ ] **T5.2** `RestClient` interceptor forwarding `X-Correlation-Id`; `correlationId` property on every Problem Details body, including 401 and 403.
  Covers: US5-AC3, NFR-5. Verify: `BalanceResilienceIT` (WireMock receives the header); `BalanceControllerTest`.
- [ ] **T5.3** Structured logging (ECS) in the `docker` profile of `balance-api` and `core-mock`; local pattern includes the correlation id.
  Covers: US5-AC5. Verify: `docker compose logs` shows one JSON object per line with `correlationId`.
- [ ] **T5.4** `balance.fallback` counter tagged `outcome`.
  Covers: US5-AC2. Verify: `ObservabilityIT`.
- [ ] **T5.5** `ObservabilityIT`: liveness `UP` with the circuit open, circuit breaker state in health, Resilience4j and fallback metrics present, OpenAPI document contains the operation, security scheme and error responses.
  Covers: US5-AC1, US5-AC2, US7-AC1. Verify: `./mvnw verify`.
- [ ] **T5.6** `spec-reviewer` for US5 and US7.

## Stage 6: Docker and final README

Commit: `docs: complete README with design decisions and live degradation demo`

- [ ] **T6.1** Container healthchecks (auth-server on JWKS, the others on liveness); `depends_on` with `condition: service_healthy`; `SPRING_PROFILES_ACTIVE=docker`; secrets from `.env`.
  Covers: US6-AC3, NFR-2. Verify: `docker compose up --build`; `docker compose ps` shows three healthy services.
- [ ] **T6.2** README: what it is, Mermaid diagram, how to run, live degradation demo (token, balance, 403, chaos, retries in logs, open circuit in Actuator, stale fallback, recovery), how to run tests, limitations, next steps.
  Covers: SC-3, US5-AC3 (cross-service logs), US6-AC3. Verify: every command in the README executed against `docker compose up`.
- [ ] **T6.3** README "Design decisions": why `RestClient`, what is retried and what is not, retry and circuit breaker order, why client credentials and scopes, why virtual threads for an I/O-bound service, when a stale fallback is acceptable, what would change in production at a bank. Links to ADRs.
  Covers: plan "Decisions". Verify: links resolve; content consistent with ADRs.
- [ ] **T6.4** Final review of the whole repository: consistency between spec, plan, code and README; English spelling and grammar; broken links; no warnings, dead code or loose `TODO`s.
  Covers: SC-1, SC-2. Verify: `./mvnw verify`; `spec-reviewer` for all stories.
