# ADR-0004: Client credentials and scopes for system-to-system access

Status: Accepted

## Context

Callers are systems acting on their own behalf (the IVR, the chat platform), not end users. The gateway must know which system is calling and restrict each system to what it needs. Options considered:

- Static API keys: simple, but long-lived, often shared, and without a standard for scopes, expiry or rotation.
- OAuth 2.0 client credentials with opaque tokens: short-lived and revocable, but every request needs an introspection call, which puts the authorization server on the hot path.
- OAuth 2.0 client credentials with signed JWTs: short-lived, carry scopes, validated locally against the issuer's public keys.
- Mutual TLS only: strong client identity at the transport layer, but no fine-grained permissions, and heavier certificate operations.

## Decision

Use OAuth 2.0 client credentials with JWT access tokens signed with RS256 by Spring Authorization Server. Each client is registered with the scopes it needs (`ivr-channel`: `balance.read`; `reporting`: `reporting.read`). `balance-api` validates signature, issuer and expiry against the JWKS, which it fetches and caches, and requires `SCOPE_balance.read` on the balance endpoint.

## Consequences

- No call to the authorization server per request. The authorization server can be briefly unavailable without affecting requests that carry valid tokens.
- Least privilege: a registered client without the scope gets `403`, and this is tested.
- A JWT cannot be revoked before it expires; the 5-minute lifetime bounds the exposure.
- The audience claim is not validated: with client credentials it defaults to the client id. A production deployment would issue an audience per resource server and validate it.
- mTLS remains a complementary transport-layer control (out of scope, next step), and token exchange would be needed to carry the end customer's identity.
