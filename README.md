# balance-gateway

[![CI](https://github.com/jcorta/balance-gateway/actions/workflows/ci.yml/badge.svg)](https://github.com/jcorta/balance-gateway/actions/workflows/ci.yml)

Reference implementation of a resilient integration service between customer-facing channels (IVR, chat) and a slow, unreliable core banking system. Channels authenticate as systems with OAuth 2.0 client credentials. Calls to the core are bounded by timeouts, retries with backoff and a circuit breaker. When the core is unavailable, the service degrades explicitly by returning the last known balance marked as stale.

## Modules

| Module | Port | Role |
|---|---|---|
| [`auth-server`](auth-server) | 9000 | Spring Authorization Server issuing `client_credentials` tokens |
| [`balance-api`](balance-api) | 8080, management 8081 | Balance API for channels: resource server, resilient core client, stale fallback |
| [`core-mock`](core-mock) | 8090 | Core banking simulator with runtime-configurable latency and failures |

## Documentation

- [Specification](specs/001-balance-gateway/spec.md): what the service must do and why.
- [Plan](specs/001-balance-gateway/plan.md): stack, architecture, API contract, resilience configuration, test strategy.
- [Tasks](specs/001-balance-gateway/tasks.md): staged implementation checklist.
- [Architecture decision records](docs/adr/README.md).
- [Repository rules](AGENTS.md): conventions, build and test commands.

## Build and run

Requirements: JDK 21 and Docker with Compose.

```sh
./mvnw verify                 # build and run all tests
cp .env.example .env          # sample client secrets
docker compose up --build     # start the three services
```

## License

[MIT](LICENSE)
