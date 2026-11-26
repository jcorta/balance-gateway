# ADR-0003: Last known balance as an explicit, bounded fallback

Status: Accepted

## Context

When the core is unavailable, the gateway can either fail the request or answer with data it already has. For an IVR the difference matters: a failure usually routes the customer to an agent, which is slow and expensive, while most balance enquiries are informational. A balance is, however, only as good as its age, because card transactions and transfers change it continuously.

A stale balance is acceptable when the consumer is informational and can qualify the answer: "your last known balance, as of 14:03, was ...". It is not acceptable when the balance drives a decision with financial consequences, such as authorising a payment or a withdrawal, checking available funds for a transfer, or a credit decision. Those paths need a current answer from the system of record, or a refusal.

## Decision

- On every successful core response, store the balance in an in-memory Caffeine cache keyed by account id, bounded by `balance.fallback.max-entries` and expiring `balance.fallback.max-age` after the write.
- When the call to the core fails for any reason other than "account not found" or another client error, answer with the cached balance, with `stale: true` and the `asOf` that the core originally reported. If there is no entry, answer `503` with Problem Details.
- Never fall back on "account not found": that answer is authoritative.
- Count fallback outcomes (`balance.fallback`, tag `outcome=stale|unavailable`).

## Consequences

- The channel always knows whether the balance is current and how old it is, so the decision of how to use it stays with the channel.
- The contract of this endpoint is informational. A payment-authorisation endpoint would have its own policy, without a stale fallback.
- The cache is per instance and is lost on restart, so behaviour during an outage depends on which instance answers and on what it has served before. A shared cache (for example Redis) would remove that limitation at the cost of another dependency and of holding balances outside the core. Listed as a next step.
- Balances are held in memory. The size bound keeps memory use predictable, and the age bound limits how long customer data stays there.
