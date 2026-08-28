# FIX-109 — Replay Production-Parity contract

## Contract
Normal Replay/Test now opens `ExecutionReplayScope` in `PRODUCTION_PARITY` mode. Replay-only FIX-091 regime persistence and transition probe authority run only when a caller explicitly opens `EXPERIMENTAL`.

## Evaluation clock
`ExecutionPriceAuthorityService.resolve` no longer accepts a null timestamp fallback to `Instant.now()`. All historical and Production callers must provide their evaluation clock explicitly.

## Price parity metadata
Each analysis test run stores `replay_logic_mode` and `replay_price_mode`. `EXACT_PRICE_REPLAY` means persisted V64 market-price events drove mechanical protection in Production order. `SIGNAL_PRICE_FALLBACK` is explicitly approximate for older windows.

## Profit lock / continuation
No stateful Production wallet service is invoked from Replay. Both paths already share `ProfitLockPolicy`; exact-price Replay already has a tick-driven continuation/protection loop matching Production ordering, so that path is intentionally preserved rather than rewritten.

## Safety boundary
Replay continues writing only test/shadow state. Wallet production classes are not called by this parity-mode change.
