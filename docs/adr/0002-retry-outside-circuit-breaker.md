# ADR-0002: Deadline, retry and circuit breaker order

Status: Accepted

## Context

Calls to the core are protected by three mechanisms: a retry for transient faults, a circuit breaker to stop calling a core that is failing, and a total deadline so the channel always gets an answer within its budget. The order in which they wrap the call changes behavior:

- Circuit breaker outside the retry: the breaker sees one outcome per logical request, after all retries. It opens later, and while it is closed every request still sends up to three calls to a core that is down.
- Retry outside the circuit breaker: the breaker records every attempt and opens sooner. Once it is open, the next attempt fails with `CallNotPermittedException`, which ends the retry immediately.
- A deadline inside the retry would bound each attempt but not the request. The per-attempt bound is already the read timeout.

Resilience4j's annotations apply a default aspect order with the retry outermost, but the order is configured through aspect-order properties away from the code, and `@TimeLimiter` only supports asynchronous return types.

## Decision

Compose `TimeLimiter( Retry( CircuitBreaker( call ) ) )` explicitly in `ResilientCoreBalanceClient`, using the instances that the Resilience4j Spring Boot starter creates from `application.yml`. `CallNotPermittedException` is not retryable. The deadline runs the chain on a virtual thread and cancels it, with interruption, when the budget expires.

## Consequences

- The order is visible in one method and covered by unit tests that use real Resilience4j objects without Spring.
- The breaker reacts to sustained failure within two or three requests, and skips the remaining retries of the request in which it opens.
- Retries count towards the breaker's failure rate. The thresholds take that into account (window of 10 calls, minimum of 5).
- Running the chain on another thread requires copying the MDC, so that the correlation id reaches the outbound call and the logs.
