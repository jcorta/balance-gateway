# Spec 001: Balance Gateway

| | |
|---|---|
| Status | Draft |
| Branch | `001-balance-gateway` |
| Plan | [plan.md](plan.md) |
| Tasks | [tasks.md](tasks.md) |

## Context

Customer-facing channels such as the IVR or the Contact Center chat need a customer's account balance while the customer is waiting on the line. The balance is owned by the core banking system, which is slow under load and fails intermittently. Channels cannot absorb that behaviour: an IVR call flow has a fixed time budget per step and must decide quickly whether to read out a balance, read out a qualified balance, or route the customer elsewhere.

The balance gateway sits between the channels and the core. Its job is to give channels a fast, predictable answer regardless of the core's state, and to make the security and resilience controls part of the architecture rather than of each channel's implementation.

## Actors

| Actor | Description |
|---|---|
| Channel system | An automated client (IVR, chat) acting on its own behalf, not on behalf of an end user. Authorised to read balances. |
| Non-balance system | Another registered automated client (for example, reporting) that is not authorised to read balances. |
| Core banking system | System of record for balances. Slow and unreliable. Trusted, internal. |
| Operator | Person or tool that monitors the gateway and its dependency on the core. |
| Identity provider | Issues short-lived credentials to registered systems. |

## User stories

Priorities: **P1** is required for the gateway to be useful; **P2** is required to run it.

### US1 (P1): Channel retrieves an account balance

As a channel system, I want to retrieve the current balance of an account so that I can tell the customer their balance.

| ID | Acceptance criterion |
|---|---|
| US1-AC1 | **Given** a channel with balance read permission and an existing account, **when** it requests the account's balance, **then** it receives the account identifier, balance amount, currency, the time at which the core produced the balance, and an indication that the balance is current. |
| US1-AC2 | **Given** a channel with balance read permission, **when** it requests the balance of an account the core does not know, **then** it receives a "not found" error in the uniform error format, and the request is not repeated against the core. |
| US1-AC3 | **Given** a channel with balance read permission, **when** it requests a balance with a malformed account identifier, **then** it receives a "bad request" error in the uniform error format and the core is not called. |

### US2 (P1): Only authorised systems can read balances

As the bank, I want only registered systems holding the balance read permission to read balances so that a compromised or misconfigured client cannot access customer data.

| ID | Acceptance criterion |
|---|---|
| US2-AC1 | **Given** a request without a credential, **when** it asks for a balance, **then** it receives an "unauthenticated" error in the uniform error format. |
| US2-AC2 | **Given** a request with an expired, tampered or foreign credential, **when** it asks for a balance, **then** it receives an "unauthenticated" error. |
| US2-AC3 | **Given** a registered system whose credential lacks the balance read permission, **when** it asks for a balance, **then** it receives a "forbidden" error in the uniform error format. |
| US2-AC4 | **Given** a registered system, **when** it presents its client identifier and secret to the identity provider, **then** it obtains a short-lived signed credential carrying only the permissions it was registered with. |

### US3 (P1): Bounded response time when the core misbehaves

As a channel system, I want an answer within a known time budget so that my call flow never stalls on the core.

| ID | Acceptance criterion |
|---|---|
| US3-AC1 | **Given** the core fails transiently (server error or no answer in time), **when** a channel requests a balance, **then** the gateway repeats the request to the core a bounded number of times with increasing waits between attempts, and returns the balance if a repetition succeeds. |
| US3-AC2 | **Given** the core rejects a request as invalid or unknown, **when** a channel requests a balance, **then** the gateway does not repeat the request. |
| US3-AC3 | **Given** the core does not answer at all, **when** a channel requests a balance, **then** the gateway answers within the configured total time budget, including all repetitions. |
| US3-AC4 | **Given** the core has failed for a configured share of recent requests, **when** further requests arrive, **then** the gateway stops calling the core for a configured period and answers immediately through the degraded path. |
| US3-AC5 | **Given** the gateway has stopped calling the core, **when** the configured period elapses, **then** it lets a limited number of trial requests through and resumes normal operation if they succeed, or stops calling the core again if they fail. |

### US4 (P1): Explicit degradation with the last known balance

As a channel system, I want to receive the last known balance, clearly marked as not current, when the core is unavailable, so that I can still serve informational requests and decide how to phrase the answer.

| ID | Acceptance criterion |
|---|---|
| US4-AC1 | **Given** the core is unavailable and the gateway obtained this account's balance from the core within the configured maximum age, **when** a channel requests the balance, **then** it receives that balance marked as not current, with the original time at which the core produced it. |
| US4-AC2 | **Given** the core is unavailable and the gateway has no balance for the account, or only one older than the configured maximum age, **when** a channel requests the balance, **then** it receives a "service unavailable" error in the uniform error format. |
| US4-AC3 | **Given** any response, **then** a balance that did not come from the core during the current request is never presented as current. |

### US5 (P2): Operators can observe the gateway and its dependency

As an operator, I want to see the health of the gateway, the state of its protection against the core, and to follow a single request across systems, so that I can diagnose incidents quickly.

| ID | Acceptance criterion |
|---|---|
| US5-AC1 | **Given** the gateway is running, **when** an operator checks its health, **then** the gateway reports itself alive even while the core is unavailable, and separately reports the state of its protection against the core (calling normally, stopped, trialling). |
| US5-AC2 | **Given** the gateway is running, **when** an operator reads its metrics, **then** they include repetitions to the core, the state of the protection against the core, and the number of degraded responses by outcome. |
| US5-AC3 | **Given** a request carrying a correlation identifier, **when** the gateway handles it, **then** the same identifier is returned to the caller, forwarded to the core, and present in the log entries of both the gateway and the core for that request. |
| US5-AC4 | **Given** a request without a correlation identifier, or with a malformed one, **when** the gateway handles it, **then** the gateway generates a new identifier and uses it as in US5-AC3. |
| US5-AC5 | **Given** the gateway is running, **then** its log entries are structured (one machine-readable record per line). |

### US6 (P2): Degradation can be reproduced on demand

As an operator or reviewer, I want to change the core simulator's behaviour at runtime so that I can demonstrate and verify the gateway's degradation without restarting anything.

| ID | Acceptance criterion |
|---|---|
| US6-AC1 | **Given** the core simulator is running, **when** an operator sets an added latency, a share of failing requests, or a full outage, **then** subsequent core requests behave accordingly, without a restart. |
| US6-AC2 | **Given** a modified simulator behaviour, **when** the operator resets it, **then** the simulator answers normally again. |
| US6-AC3 | **Given** a fresh checkout, **when** a reviewer starts the system with a single command, **then** all components start in dependency order and report themselves healthy. |

### US7 (P2): The API contract is discoverable

As a channel developer, I want a machine-readable description of the API and a browsable view of it, so that I can integrate without reading the gateway's code.

| ID | Acceptance criterion |
|---|---|
| US7-AC1 | **Given** the gateway is running, **when** a developer requests the API description, **then** it documents the balance operation, its security requirement and every error response. |

## Edge cases

- The core answers with a body that cannot be interpreted: treated as a core failure (degraded path), not repeated.
- The core answers slowly but within the per-attempt limit: the answer is used; no repetition.
- The core recovers while the gateway has stopped calling it: the gateway keeps answering through the degraded path until the configured period elapses (US3-AC5).
- The protection opens in the middle of a request's repetitions: the remaining repetitions are skipped and the degraded path answers immediately.
- A correlation identifier longer than allowed or containing characters outside the allowed set is replaced, not forwarded, to prevent log injection.
- A balance becomes available from the core again: it replaces the stored last known balance, so the maximum age restarts.
- Several gateway instances: each holds its own last known balances; a channel may receive a degraded answer from one instance and none from another (accepted limitation, see "Out of scope").

## Non-functional requirements

| ID | Requirement |
|---|---|
| NFR-1 Latency | Every response, successful or not, is produced within a total time budget that is configurable and defaults to 2 seconds. While the gateway is not calling the core, degraded responses take under 100 ms at the gateway. |
| NFR-2 Security | Systems authenticate as themselves with short-lived signed credentials. Access follows least privilege through per-system permissions. Error responses never include stack traces or internal details. Secrets are supplied through the environment and never stored in the repository. Containers run as a non-root user. |
| NFR-3 Operability | Health, metrics and logs are available without code changes. All timeouts, repetition limits, protection thresholds and the maximum age of a last known balance are configurable per environment. |
| NFR-4 Reproducibility | The whole system starts with one command on a developer machine with Docker. The build runs all automated tests with one command and in continuous integration on every push and pull request. |
| NFR-5 Uniform errors | All error responses use a single documented, standard format that includes a correlation identifier. |

## Out of scope

Persistent storage, a shared or distributed cache of last known balances, messaging, Kubernetes, cloud deployment, a user interface, rate limiting, concurrency isolation between dependencies (bulkhead), mutual TLS, service mesh, token exchange on behalf of end users, and any operation that moves money.

## Success criteria

- SC-1: Every acceptance criterion is covered by at least one automated test, or by a step of the documented demo when automation is not practical.
- SC-2: The build with all tests passes locally and in continuous integration.
- SC-3: A reviewer can run the end-to-end demo described in the README in under five minutes: obtain a credential, read a balance, be denied without permission, degrade the core, observe repetitions and the protection opening, receive a degraded balance, restore the core and observe recovery.
