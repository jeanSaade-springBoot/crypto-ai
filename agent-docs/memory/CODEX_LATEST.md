# NON-AUTHORITATIVE PEER POSITION  - CODEX
Updated: 2026-10-16 03:02:32
Run: 20261016-024616
Round: 2
Status: NOT CONVERGED

THIS FILE IS NOT ESTABLISHED FACT.
It is a standing claim/position only.
It is NOT dual-agreed knowledge.
It is NOT an approved decision.
It is NOT current architecture unless current code/database/log/test evidence confirms it.
Current code/database/log/test evidence outranks this file.
Proposed future architecture must not be read as implemented architecture.
Authoritative shared memory is only:
- agent-docs/AGREED_KNOWLEDGE.md
- agent-docs/DECISIONS.md
## FINDINGS

- **PROJECT EVIDENCE:** Not freeze-ready. P01–P07 are explicitly pending, including the canonical specification, evidence E01–E10, test gates, and acceptance methodology. [Preparation:53](/C:/workspace/crypto-ai-trader-mysql/agent-docs/FIX-156_IMPLEMENTATION_PREPARATION.md:53)

- **PROJECT EVIDENCE:** The canonical contract is still incompatible with the owner’s Replay-only/fixed-notional direction: it is `RPR-0.4-DRAFT` and describes a virtual wallet, account profile, and shared entry policies. The preparation instead relies on an external RPR-0.9.12 draft and references an absent “Section 3.3.1” for exit precedence. [Canonical spec:3](/C:/workspace/crypto-ai-trader-mysql/agent-docs/FINAL_CHANGE_SPECIFICATION.md:3) [Old flow:17](/C:/workspace/crypto-ai-trader-mysql/agent-docs/FINAL_CHANGE_SPECIFICATION.md:17) [Dangling reference:167](/C:/workspace/crypto-ai-trader-mysql/agent-docs/FIX-156_IMPLEMENTATION_PREPARATION.md:167)

- **PROJECT EVIDENCE:** “Zero Production code” is not enforceable in the registry. R156-05 and R156-06 name shared classes; `ExecutionReplayScope` is a Spring component used by Production execution/validation services. The frozen specification must explicitly prohibit edits to shared/Production classes and require Replay-owned readers/adapters instead. [Registry:72](/C:/workspace/crypto-ai-trader-mysql/agent-docs/FIX-156_IMPLEMENTATION_PREPARATION.md:72) [Shared component:24](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/execution/service/ExecutionReplayScope.java:24)

- **PROJECT EVIDENCE:** Independent of the waived `wallet_settings` issue, current Replay still calls Binance minimum-order evaluation in both price-driven and signal-fallback near-TP flows. This violates the frozen-fixture/no-HTTP rule and is a remaining blocker. [Live-price path:534](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/regression/service/ShadowProductionReplayService.java:534) [Fallback path:687](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/regression/service/ShadowProductionReplayService.java:687)

- **PROJECT EVIDENCE:** Exit parity is still materially unresolved. Production TP-extension rebasing passes literal `true` into the profit-lock policy, whereas Replay passes the configurable toggle. This confirms D22 and needs an exact fixture oracle. [Production:254](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/DynamicProfitLockService.java:254) [Replay:779](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/regression/service/ShadowProductionReplayService.java:779)

- **PROJECT EVIDENCE:** The existing FIX-156 foundation contradicts the preparation’s statement that no FIX-156 code or migration has started. It adds V110 and Replay classes, but the current Replay path does not use them. More seriously, V110 keys `replay_policy_profile` by `run_id`, while “Delete Data” resets `analysis_test_run` IDs without deleting any `replay_*` rows. [Preparation:429](/C:/workspace/crypto-ai-trader-mysql/agent-docs/FIX-156_IMPLEMENTATION_PREPARATION.md:429) [V110:2](/C:/workspace/crypto-ai-trader-mysql/src/main/resources/db/migration/V110__fix_156_replay_owned_tables.sql:2) [Purge/reset:362](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/regression/service/RegressionTestService.java:362)

- **AGENT INFERENCE:** If V110 is applied and the current Delete Data feature is used, a reused run ID can cause a duplicate-profile insert or stale fixture/output association. This must be registered and fixed before any wiring.

- **PROJECT EVIDENCE:** The required future-Production-change mirror rule is absent. “Version-matched mirrors” is stated, but there is no mandatory Production-change impact row, source/version fingerprint comparison, or CI test requiring Replay mirror/fixture updates. [R156-09:76](/C:/workspace/crypto-ai-trader-mysql/agent-docs/FIX-156_IMPLEMENTATION_PREPARATION.md:76)

- **PROJECT EVIDENCE:** Current BUY validation includes FIX-154 higher-timeframe decisions and in-memory metrics. A Replay-local BUY mirror must explicitly include the applicable FIX-154 decision behavior—or classify a historical case as non-comparable. The preparation does not provide an independently reviewable in-repository rule register for this. [BUY path:93](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/TradeExecutionValidationService.java:93) [Metrics:416](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/TradeExecutionValidationService.java:416)

## EVIDENCE

- **PROJECT EVIDENCE:** I performed read-only source/document inspection only; no tests, database queries, services, deployments, or Production systems were accessed.
- **PROJECT EVIDENCE:** The current price query orders rows by `(observed_at, id)`, but its DTO omits both ID and delivery status; Replay subsequently sorts only by timestamp. This is insufficient for required event-ID logging/certification, but does not itself prove current same-timestamp ordering is wrong. [Query:118](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/market/service/MarketPriceEventService.java:118) [DTO:173](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/market/service/MarketPriceEventService.java:173) [Replay sort:89](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/regression/service/ShadowProductionReplayService.java:89)

## OBJECTIONS

- **AGENT INFERENCE:** Claude’s claim that Replay must either modify Production code or duplicate the whole 2,600-line execution service is unsupported. The owner-selected Replay-local BUY mirror is a viable third design; it needs bounded, versioned parity tests and future-change enforcement.

- **PROJECT EVIDENCE:** `wallet_settings` is not listed as a remaining blocker here, per the owner rule. That waiver does not permit shared `validateBuy`, live HTTP, or account-table fall-through; those are independently prohibited by the preparation. [Owner rule:40](/C:/workspace/crypto-ai-trader-mysql/agent-docs/FIX-156_IMPLEMENTATION_PREPARATION.md:40)

- **AGENT INFERENCE:** A table-name allow-list cannot enforce the preparation’s statement-specific `fix122_evaluation` exception. The current foundation confirms this limitation because it only accepts four Replay tables and rejects `fix122_evaluation` entirely. [Policy:10](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/regression/replay/ReplayAccessPolicy.java:10)

## UNKNOWNS

- **PROJECT EVIDENCE:** E03–E10, source/configuration/migration freeze evidence, confidence method, denominator, coverage floor, and sealed acceptance cohort remain unprovided.
- **AGENT INFERENCE:** The external RPR-0.9.12 content cannot be independently frozen from this repository, so its complete D/S register and precise exit-order oracle remain unverified.
- **AGENT INFERENCE:** No current evidence establishes whether historical exchange constraints and signal-availability timestamps can be reconstructed for the intended cohort.

## FINDINGS FOR JEAN

- **AGENT INFERENCE:** Do not freeze yet.
- **AGENT INFERENCE:** First create one hashed canonical specification in-repository: exact non-wallet decision/exit precedence, FIX-154 mirror treatment, no-Production-file boundary, V110/DB disposition, and the run-ID lifecycle correction.
- **AGENT INFERENCE:** Add a binding rule: every future Production decision/exit enhancement requires a Replay-impact registry entry and corresponding mirror/fixture/contract-test update before it is accepted.
- **AGENT INFERENCE:** Keep `wallet_settings` waived as requested, but retain the separate HTTP, shared-table, replay-run-ID, missing evidence, and undefined-precedence blockers.

VERDICT: REJECTED
