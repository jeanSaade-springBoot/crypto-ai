# DATABASE QUERY LOG - db-wrapper-test-20261009

Password is never logged.

## DBQ-D01
- timestamp: 2026-10-09 23:46:25
- requesting_agent: COORDINATOR
- run_id: db-wrapper-test-20261009
- query_id: DBQ-D01
- purpose: TEST-D-forbidden-create
- sql_file: DBQ-D01-COORDINATOR.sql
- result_file: DBQ-D01-COORDINATOR-result.txt
- blocked_by_wrapper: yes
- exit_code: 2
- duration_ms: 11
- row_count: n/a
- error: Rejected statement family 'CREATE'. Allowed: SELECT, SHOW, DESCRIBE, DESC, EXPLAIN.
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

