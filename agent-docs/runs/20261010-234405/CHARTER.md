# FAILURE TRADE INDEPENDENT COMPARATIVE STUDY

**Status:** ANALYSIS-ONLY. No application code changes, trading-rule changes, database writes, configuration changes, deployment, Git push/merge/commit, or implementation authorization.

**Purpose:** Determine WHY trades failed, whether entries were actually wrong, what route each trade followed, what evidence proves the diagnosis, and what improvements may deserve future investigation. A losing trade is NOT assumed to be a system defect. NORMAL MARKET LOSS is a valid conclusion.

**Charter note:** Section 26 was truncated in the originating request. Sections 1-25 below are the contract.

## 1. Two completely independent analyst tracks

TRACK A = CODEX. TRACK B = CLAUDE.

During Iterations 1-8: no sharing of findings, no review of the other agent, no argument, no consensus, no visibility of the other agent's selected trades before own selection is frozen. Other-agent peer-memory must not influence this study.

Shared authoritative knowledge allowed: AGENTS.md, AGREED_KNOWLEDGE.md, DECISIONS.md, current source, current project documentation. Each agent may use its OWN historical knowledge.

Coordinator compares only AFTER both Iteration 8 reports are FROZEN.

## 2. Monitoring windows

WINDOW A: 10:00 KSA. WINDOW B: 18:00 KSA.

Each agent independently analyzes 6 failed trades per window (12 total). If fewer than 6 exist: report the actual number; do not silently substitute; do not expand the window without documenting it.

State the exact monitoring tolerance around 10:00 KSA and 18:00 KSA. Document selection methodology.

Calendar context supplied by orchestrator: 2026-10-10. Each agent independently decides which date(s) apply from evidence.

## 3. Time policy

MySQL timestamps = UTC. Binance timestamps = UTC. Backend/Replay interprets timestamps = UTC. Human reporting = KSA (UTC+3). Never label raw database UTC as KSA. Important timing evidence shows both UTC and KSA. Final tables: Entry Time KSA, Exit Time KSA.

## 4. Failed trade definition

Determine failed trades from actual project evidence. Do not assume every negative P/L row is equivalent without checking lifecycle. Document exact failure-selection criteria. A trade may still be NORMAL_MARKET_LOSS when P/L is negative.

## 5. Available evidence

Read-only: source, crypto_ai via approved wrapper (coordinator-executed DB_REQUEST), local logs, monitoring, trade_signal, candles, positions/trades, decision_path, analysis_breakdown, market_context, indicators, component scores, total scores, strategy, vetoes, authority, entry-quality, opportunity, protection, position-management, exit, wallet settings when relevant, Replay, Production-safe retained evidence, tests, config, historical investigations, AGREED_KNOWLEDGE, DECISIONS.

Public internet research allowed (academic, microstructure, indicators, risk, official docs). Do not upload private DB/logs/credentials/tokens/secrets/proprietary source. Do not execute downloaded scripts.

## 6. Evidence classification

PROJECT EVIDENCE / EXTERNAL RESEARCH / AGENT INFERENCE. External research is not proof a strategy improves this project.

## 7. Eight independent self-review iterations

Not Codex-vs-Claude debate.

1. Trade identification and reconstruction; freeze selected set.
2. Database forensics with exact tables, columns, query IDs, why checked, findings, missing evidence.
3. Log and timing forensics with exact files, identifiers, UTC/KSA, missing events, contradictions, ordering.
4. Code and trade route reconstruction (candle -> indicators -> scores -> signal -> strategy -> filters -> authority -> entry -> position -> protection -> management -> exit -> P/L). Explain WHY entry was permitted.
5. Entry quality: WAS THE ENTRY WRONG? YES / NO / PARTIALLY / UNPROVEN. Do not call an entry wrong merely because the trade lost.
6. Root cause and external research. Reject indicator duplication. Do not add complexity to fix one historical trade.
7. Self-challenge / attempt to disprove own diagnosis.
8. Final independent classification. Freeze report. Do not change after seeing the other report (you will not see it during this study).

## 8. Failure classification

One PRIMARY classification per trade. Allowed examples: BAD_ENTRY, EARLY_ENTRY, LATE_ENTRY, FALSE_BREAKOUT, BAD_MARKET_REGIME, WEAK_MOMENTUM, VOLUME_FAILURE, MTF_CONFLICT, BTC_CONTEXT, ATR_VOLATILITY, ENTRY_QUALITY_FAILURE, RISK_REWARD_FAILURE, EXIT_FAILURE, PROTECTION_FAILURE, POSITION_MANAGEMENT_FAILURE, DATA_TIMING_DEFECT, STALE_STATE, IMPLEMENTATION_DEFECT, NORMAL_MARKET_LOSS, OTHER (explain). Secondary contributors allowed.

## 9. Indicator / component scores

Reconstruct ACTUAL decision-time scores when available. Do not invent. Do not use future information. If unavailable: NOT AVAILABLE. Show TOTAL SCORE when provable.

## 10. Proof standard

Material claims reference CODE-*, DBQ-*, LOG-*, REPLAY-*, WEB-*. Do not use confidence alone as proof.

## 11. Suggested fix

NO CHANGE, or evidence-backed candidate. Scope: TRADE_SPECIFIC, FAILURE_CLASS, or SYSTEMIC. Prefer class/systemic. Trade-specific = high overfitting risk. Explain what/why/failure/existing-indicator overlap/benefit/downside/required Replay validation. No implementation authorized.

## 12-13. Final reports

CODEX_FAILED_TRADE_STUDY.md and CLAUDE_FAILED_TRADE_STUDY.md. Same summary fields and same table columns. Do not combine results.

Summary fields: Agent, Window A, Window A qualifying trades, Window B, Window B qualifying trades, Total analyzed, Database tables checked, Log files checked, Code areas checked, Replay runs checked, External sources used, Evidence limitations.

Table columns: Trade ID | Symbol | Entry Time KSA | Exit Time KSA | P/L | Trade Route | Why Entry Was Allowed | Indicator / Component Scores | Total Score | Entry Wrong? | Primary Failure Root | Secondary Contributors | Proof / Evidence IDs | Failure Mechanism | Suggested Fix | Fix Scope | Confidence

## 14. Mandatory database detail

crypto_ai. Table of exact table names, checked-for, query IDs, main finding. Columns inspected, limitations. TABLES CONSIDERED BUT NOT CHECKED. Counts: actually queried / identified / not queried.

## 15. Mandatory log detail

Exact log file/path, search keys, event IDs, UTC, KSA, missing events, contradictions, limitations. Do not merely say "logs checked."

## 16. Mandatory code detail

Class/file, method/area, why checked, finding, evidence ID.

## 17-20. Live visibility

LIVE_TEAM_STATUS.md and LIVE_EVENTS.log. Observer commands: what are they doing, analysis history, analysis status full. Those commands must not stop/restart/launch rounds or modify memory/consensus.

## 21. Coordinator neutrality

Orchestrator only during 1-8. No recommending root cause, no forcing agreement, no changing selected trades, no influencing classification.

## 22-25. Post-study comparison (AFTER BOTH REPORTS FROZEN)

COORDINATOR_FAILED_TRADE_COMMON_FINDINGS.md with overview, common trade findings table (Agreement Level FULL/STRONG/PARTIAL/NONE), and common failure patterns independently identified by BOTH agents.

## 26

Not supplied in the originating message. Do not invent additional mandatory sections.
