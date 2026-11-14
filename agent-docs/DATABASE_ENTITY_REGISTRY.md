# DATABASE ENTITY REGISTRY

Durable knowledge about `crypto_ai` entities.
Database target: `crypto_ai` only. Do not mix with `crypto_ai_v2`.

One agent's discovery is a claim, not a registry fact.
A durable update requires the dual-agent agreement process in `agent-docs/COORDINATOR.md`
(same exact text; Codex=AGREED and Claude=AGREED).

Status values: `CANDIDATE`, `DUAL_AGREED`, `SUPERSEDED`.

Time policy: raw timestamp columns are UTC unless a dual-agreed entry proves otherwise.
Never describe a raw DB timestamp as KSA. Convert as UTC then KSA=UTC+3h.

## Required entry format

### [REG-ID] table_name

- schema:
- table:
- purpose:
- primary key:
- important columns:
- timestamps and timezone meaning:
- relationships:
- producer:
- consumers:
- Replay relevance:
- Production relevance:
- useful investigation queries:
- evidence/source:
- confidence/status:
- Codex:
- Claude:

## Entries

No dual-agreed entity entries recorded yet.
