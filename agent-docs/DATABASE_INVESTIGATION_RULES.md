# Database investigation rules

Coordinator database: `crypto_ai` on localhost:3307 as `ai_investigator`.
Do not mix with `crypto_ai_v2`.

## Credentials
Agents never receive the database password.
The coordinator/wrapper reads `AGENT_DB_PASSWORD` from the local environment only.
Do not write that value into AGENTS.md, COORDINATOR.md, Cursor rules, docs, prompts, logs, or source.

Other settings (defaults in parentheses):

- `AGENT_DB_HOST` (localhost)
- `AGENT_DB_PORT` (3307)
- `AGENT_DB_NAME` (crypto_ai)
- `AGENT_DB_USER` (ai_investigator)
- `AGENT_MYSQL_EXE` (local mysql.exe)

## Approved access path
Agents request evidence. The coordinator executes:

`scripts/agent-db-query.ps1`

The MySQL account is database-level read-only. The wrapper is an additional safety layer.

Allowed statement families: `SELECT`, `SHOW`, `DESCRIBE`/`DESC`, `EXPLAIN`.
Everything else is rejected by default, including writes, DDL, grants, CALL, SET, LOAD, locks, and transaction control.
Multiple statements are allowed only if the wrapper proves each statement independently read-only.

## Audit
Each query is stored under `agent-docs/runs/<run-id>/db/` with SQL, result, and `DATABASE_QUERY_LOG.md`.
Never log the password.

## Time
Raw MySQL/Binance timestamps are UTC. Never describe a raw DB timestamp as KSA.
When converting for a report:

- UTC: `<raw time>`
- KSA: `<UTC + 3h>`

## Registry
`agent-docs/DATABASE_ENTITY_REGISTRY.md` is durable entity knowledge.
One agent's discovery is not fact. Append only through dual-agent agreement (`AGREED`/`AGREED`).
Proposed future schema is not current schema.

## Scope examples
Harmless reads may cover `trade_signal`, trades/positions, candles, decision/analysis/market-context tables, Replay/test identifiers, and related trader entities in `crypto_ai` only.
