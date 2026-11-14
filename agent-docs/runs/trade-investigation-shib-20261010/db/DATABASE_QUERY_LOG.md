# DATABASE QUERY LOG - trade-investigation-shib-20261010

Password is never logged.

## DBQ-001
- timestamp: 2026-10-10 00:54:08
- requesting_agent: CODEX
- run_id: trade-investigation-shib-20261010
- query_id: DBQ-001
- purpose: Read wallet trade schema for requested SHIB investigation
- sql_file: DBQ-001-CODEX.sql
- result_file: DBQ-001-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 276
- row_count: 23
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-002
- timestamp: 2026-10-10 00:54:15
- requesting_agent: CODEX
- run_id: trade-investigation-shib-20261010
- query_id: DBQ-002
- purpose: Locate requested SHIB trades around reported KSA time; timestamps are stored UTC
- sql_file: DBQ-002-CODEX.sql
- result_file: DBQ-002-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 290
- row_count: 8
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-003
- timestamp: 2026-10-10 00:54:31
- requesting_agent: CODEX
- run_id: trade-investigation-shib-20261010
- query_id: DBQ-003
- purpose: Read trade signal schema for SHIB trade timeline investigation
- sql_file: DBQ-003-CODEX.sql
- result_file: DBQ-003-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 301
- row_count: 135
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-004
- timestamp: 2026-10-10 00:54:38
- requesting_agent: CODEX
- run_id: trade-investigation-shib-20261010
- query_id: DBQ-004
- purpose: Read position schema for SHIB exit investigation
- sql_file: DBQ-004-CODEX.sql
- result_file: DBQ-004-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 292
- row_count: 17
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-005
- timestamp: 2026-10-10 00:54:48
- requesting_agent: CODEX
- run_id: trade-investigation-shib-20261010
- query_id: DBQ-005
- purpose: Read bounded SHIB position evidence for requested exit investigation
- sql_file: DBQ-005-CODEX.sql
- result_file: DBQ-005-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 255
- row_count: 1
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-006
- timestamp: 2026-10-10 00:55:06
- requesting_agent: CODEX
- run_id: trade-investigation-shib-20261010
- query_id: DBQ-006
- purpose: Check SHIB signals at reported 11:44 KSA windows on 9 and 10 October
- sql_file: DBQ-006-CODEX.sql
- result_file: DBQ-006-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 249
- row_count: n/a
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-007
- timestamp: 2026-10-10 00:57:03
- requesting_agent: CODEX
- run_id: trade-investigation-shib-20261010
- query_id: DBQ-007
- purpose: Read requested SHIB signal 89469 entry/risk/context evidence
- sql_file: DBQ-007-CODEX.sql
- result_file: DBQ-007-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 152
- row_count: 1
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-008
- timestamp: 2026-10-10 00:57:12
- requesting_agent: CODEX
- run_id: trade-investigation-shib-20261010
- query_id: DBQ-008
- purpose: Measure SHIB post-exit price path through reported 12:15 KSA horizon
- sql_file: DBQ-008-CODEX.sql
- result_file: DBQ-008-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 106
- row_count: 2
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-009
- timestamp: 2026-10-10 00:57:17
- requesting_agent: CODEX
- run_id: trade-investigation-shib-20261010
- query_id: DBQ-009
- purpose: Locate SHIB post-exit peak candles after position 35
- sql_file: DBQ-009-CODEX.sql
- result_file: DBQ-009-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 141
- row_count: 12
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-010
- timestamp: 2026-10-10 00:57:25
- requesting_agent: CODEX
- run_id: trade-investigation-shib-20261010
- query_id: DBQ-010
- purpose: Reconstruct SHIB signal context at profit-lock exit
- sql_file: DBQ-010-CODEX.sql
- result_file: DBQ-010-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 311
- row_count: 21
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-011
- timestamp: 2026-10-10 00:57:33
- requesting_agent: CODEX
- run_id: trade-investigation-shib-20261010
- query_id: DBQ-011
- purpose: Trace SHIB immediate candles before and after profit-lock exit
- sql_file: DBQ-011-CODEX.sql
- result_file: DBQ-011-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 182
- row_count: 16
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-012
- timestamp: 2026-10-10 00:57:48
- requesting_agent: CODEX
- run_id: trade-investigation-shib-20261010
- query_id: DBQ-012
- purpose: Locate managed-position evidence table for SHIB investigation
- sql_file: DBQ-012-CODEX.sql
- result_file: DBQ-012-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 226
- row_count: 8
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-013
- timestamp: 2026-10-10 00:57:58
- requesting_agent: CODEX
- run_id: trade-investigation-shib-20261010
- query_id: DBQ-013
- purpose: Read managed position schema for SHIB profit-lock evidence
- sql_file: DBQ-013-CODEX.sql
- result_file: DBQ-013-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 134
- row_count: 39
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-014
- timestamp: 2026-10-10 00:58:04
- requesting_agent: CODEX
- run_id: trade-investigation-shib-20261010
- query_id: DBQ-014
- purpose: Read persisted profit-lock state for requested SHIB signal
- sql_file: DBQ-014-CODEX.sql
- result_file: DBQ-014-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 158
- row_count: 1
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-004
- timestamp: 2026-10-10 01:22:22
- requesting_agent: CODEX
- run_id: trade-investigation-shib-20261010
- query_id: DBQ-004
- purpose: Verify UTC versus KSA storage for SHIB signal 89469 position timestamps
- sql_file: DBQ-004-CODEX.sql
- result_file: DBQ-004-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 419
- row_count: 1
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-005
- timestamp: 2026-10-10 01:22:44
- requesting_agent: CODEX
- run_id: trade-investigation-shib-20261010
- query_id: DBQ-005
- purpose: Derive actual UTC and KSA timestamps for SHIB signal 89469 with MySQL SYSTEM KSA session
- sql_file: DBQ-005-CODEX.sql
- result_file: DBQ-005-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 267
- row_count: 1
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-006
- timestamp: 2026-10-10 01:25:15
- requesting_agent: CODEX
- run_id: trade-investigation-shib-20261010
- query_id: DBQ-006
- purpose: Verify KSA and UTC timestamps plus decision fields for SHIB signal 89469
- sql_file: DBQ-006-CODEX.sql
- result_file: DBQ-006-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 170
- row_count: 1
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

