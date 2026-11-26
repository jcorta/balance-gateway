# AGENTS.md

Rules for anyone (person or agent) changing this repository. `CLAUDE.md` imports this file; keep the rules here only.

## What this is

`balance-gateway` is a reference implementation of a resilient integration service between customer-facing channels (IVR, chat) and a slow, unreliable core banking system. The specification lives in [`specs/001-balance-gateway/`](specs/001-balance-gateway/) and design decisions in [`docs/adr/`](docs/adr/).

## Stack

| Component | Version |
|---|---|
| Java | 21 (Eclipse Temurin) |
| Spring Boot | 3.5.16 (manages Spring Framework 6.2, Spring Security 6.5, Spring Authorization Server 1.5) |
| Resilience4j | 2.4.0 (`resilience4j-spring-boot3`) |
| springdoc-openapi | 2.10.1 |
| Caffeine | managed by Spring Boot |
| WireMock | 3.13.2 (`wiremock-standalone`, tests only) |
| Maven | 3.9.16 through the Maven Wrapper |
| Docker Compose | v2 file format, `docker compose` CLI |

Versions are pinned in the root `pom.xml`. Do not add a dependency without pinning it, and check the current documentation before using an API; do not guess signatures.

## Modules

Maven multi-module build with a single parent `pom.xml` and one wrapper at the root.

| Module | Port | Responsibility |
|---|---|---|
| `auth-server` | 9000 | Spring Authorization Server. Issues `client_credentials` JWTs (RS256) and exposes the JWKS. |
| `balance-api` | 8080 (management 8081) | Channel-facing balance API. OAuth 2.0 resource server, resilient client to the core, stale-balance fallback. |
| `core-mock` | 8090 | Core banking simulator with runtime-configurable latency, error rate and outage. Internal, unauthenticated. |

Base package: `io.github.jcorta.balancegateway.<module>` (`auth`, `api`, `coremock`).

## Build and test

```sh
./mvnw test     # unit and slice tests (Surefire), no network, no Docker
./mvnw verify   # everything above plus integration tests (Failsafe)
```

On Windows use `mvnw.cmd` or run the commands from Git Bash.

| Level | Naming | Plugin / phase | Scope |
|---|---|---|---|
| Unit | `*Test` | Surefire, `test` | No Spring context. Class under test instantiated directly, collaborators mocked with Mockito. |
| Slice | `*Test` | Surefire, `test` | `@WebMvcTest` with `spring-security-test`. Web layer and security only; no auth server. |
| Integration | `*IT` | Failsafe, `integration-test` / `verify` | `@SpringBootTest` with WireMock standing in for the core, over real HTTP. |

A task in `tasks.md` is not done, and nothing is committed, while `./mvnw verify` fails. For Claude Code, `.claude/settings.json` registers a `Stop` hook (`.claude/hooks/verify-on-stop.sh`) that runs `./mvnw -q verify` at the end of each turn and reports failures back to the agent.

## Run

```sh
cp .env.example .env
docker compose up --build
```

`docker compose up` starts the three services; `balance-api` waits for the other two to be healthy. The README describes the end-to-end demo.

## Code conventions

- Code, comments, documentation and commit messages in English.
- Records for DTOs and value objects. Constructor injection only; no field injection.
- `RestClient` for outbound HTTP. Do not use `RestTemplate` or `WebClient`.
- Configuration in `application.yml` with `local` and `docker` profiles. No hard-coded URLs, timeouts or resilience thresholds.
- Errors to clients as RFC 9457 Problem Details (`ProblemDetail`). Never expose stack traces or internal exception messages.
- Comments only to explain a design reason that the code cannot express. No commented-out code, no dead code, no loose `TODO`s (pending work goes to "Next steps" in the README), no `System.out`.
- The build must compile without warnings.
- Test names describe behaviour: `returnsStaleBalanceWhenCoreIsDown`, not `test1`.
- Names are consistent across modules, packages, classes, endpoints and properties. Application properties use the `balance.*` prefix in `balance-api` and `core.*` in `core-mock`.
- No secrets in the repository. Client secrets come from environment variables; `.env.example` holds sample values.

## Commits and branches

- Conventional Commits: `feat:`, `fix:`, `test:`, `docs:`, `build:`, `ci:`, `chore:`, `refactor:`.
- One commit per stage of `tasks.md`, with `tasks.md` updated in the same commit.
- Feature work happens on the branch named after the spec (`001-balance-gateway`). Do not push or merge to `main`.

## Spec-driven workflow

1. `spec.md` states what and why, without technology.
2. `plan.md` states how. If implementation deviates from the plan, update `plan.md` (and the affected ADR) in the same commit.
3. `tasks.md` lists small ordered tasks; each references the acceptance criteria it covers and how it is verified.
4. At the end of each stage, the `spec-reviewer` subagent (`.claude/agents/spec-reviewer.md`) checks that every acceptance criterion of the stage's stories is covered by a test or a documented demo step.

## Do not

- Add a database, messaging, Kubernetes manifests, cloud resources or a frontend. They are out of scope (see `spec.md`).
- Retry on 4xx responses, or retry non-idempotent operations.
- Return a stale balance without `stale: true` and its original `asOf`.
- Weaken security to make a test pass; use `spring-security-test` instead.
- Commit `.env`, IDE files or build output.
