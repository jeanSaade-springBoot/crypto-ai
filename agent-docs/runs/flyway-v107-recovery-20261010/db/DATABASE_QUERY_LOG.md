# DATABASE QUERY LOG - flyway-v107-recovery-20261010

Password is never logged.

## DBQ-001
- timestamp: 2026-10-10 01:49:25
- requesting_agent: CODEX
- run_id: flyway-v107-recovery-20261010
- query_id: DBQ-001
- purpose: Read Flyway history for V107 checksum mismatch diagnosis
- sql_file: DBQ-001-CODEX.sql
- result_file: DBQ-001-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 109
- row_count: 2
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-002
- timestamp: 2026-10-10 01:49:54
- requesting_agent: CODEX
- run_id: flyway-v107-recovery-20261010
- query_id: DBQ-002
- purpose: Compare applied V107 schema with current migration before safe Flyway recovery
- sql_file: DBQ-002-CODEX.sql
- result_file: DBQ-002-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 0
- duration_ms: 110
- row_count: 27
- error: none
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

