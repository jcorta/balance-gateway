# ADR-0001: `RestClient` for calls to the core

Status: Accepted

## Context

`balance-api` makes one synchronous HTTP call per request to the core. It runs on the servlet stack with virtual threads, so blocking I/O is cheap. Spring offers three clients: `RestTemplate`, which is in maintenance mode; `WebClient`, which is reactive and pulls in Project Reactor; and `RestClient`, a synchronous client with a fluent API, introduced in Spring Framework 6.1 and the recommended client in Spring Framework 7. Whichever is used needs explicit connect and read timeouts, because the defaults are infinite or OS-dependent.

## Decision

Use `RestClient` backed by the JDK `HttpClient` (`JdkClientHttpRequestFactory`), built once as a bean with the connect and read timeouts from `balance.core.*` and an interceptor that forwards `X-Correlation-Id`. Status handling maps a core `404` to `AccountNotFoundException` and leaves other `4xx` and `5xx` responses as Spring's `HttpClientErrorException` and `HttpServerErrorException`, which the retry and the circuit breaker classify.

## Consequences

- Synchronous, readable code with the same programming model as the rest of the service. Virtual threads remove the scalability argument for a reactive client.
- No dependency on Reactor, and no use of the `RestTemplate` API, which receives no new features.
- The JDK client needs no extra dependency. Its connection pool is less configurable than Apache HttpClient 5's; switching is a change to the request factory only.
- A declarative `@HttpExchange` interface was not used: with a single endpoint it adds indirection without reducing code.
