# DATABASE QUERY LOG - system-health-154-diagnosis-20261010

Password is never logged.

## DBQ-001
- timestamp: 2026-10-10 01:26:48
- requesting_agent: CODEX
- run_id: system-health-154-diagnosis-20261010
- query_id: DBQ-001
- purpose: Locate enabled-symbol configuration for current system health diagnosis
- sql_file: DBQ-001-CODEX.sql
- result_file: DBQ-001-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 233
- row_count: 6
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-002
- timestamp: 2026-10-10 01:27:06
- requesting_agent: CODEX
- run_id: system-health-154-diagnosis-20261010
- query_id: DBQ-002
- purpose: Identify current signal and candle staleness causing System Health critical state
- sql_file: DBQ-002-CODEX.sql
- result_file: DBQ-002-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 2291
- row_count: 63
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-003
- timestamp: 2026-10-10 01:27:45
- requesting_agent: CODEX
- run_id: system-health-154-diagnosis-20261010
- query_id: DBQ-003
- purpose: Locate signal work records for current stale-signal diagnosis
- sql_file: DBQ-003-CODEX.sql
- result_file: DBQ-003-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 198
- row_count: 2
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-004
- timestamp: 2026-10-10 01:27:50
- requesting_agent: CODEX
- run_id: system-health-154-diagnosis-20261010
- query_id: DBQ-004
- purpose: Inspect signal work schema for current stale-signal diagnosis
- sql_file: DBQ-004-CODEX.sql
- result_file: DBQ-004-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 129
- row_count: 15
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-005
- timestamp: 2026-10-10 01:27:59
- requesting_agent: CODEX
- run_id: system-health-154-diagnosis-20261010
- query_id: DBQ-005
- purpose: Check pending or failed signal-processing work behind current stale 1m signals
- sql_file: DBQ-005-CODEX.sql
- result_file: DBQ-005-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 965
- row_count: 63
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

