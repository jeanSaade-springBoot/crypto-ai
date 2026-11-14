# DATABASE QUERY LOG - db-connect-validate-20261010

Password is never logged.

## DBQ-A01
- timestamp: 2026-10-10 00:08:17
- requesting_agent: COORDINATOR
- run_id: db-connect-validate-20261010
- query_id: DBQ-A01
- purpose: TEST-A
- sql_file: DBQ-A01-COORDINATOR.sql
- result_file: DBQ-A01-COORDINATOR-result.txt
- blocked_by_wrapper: no
- exit_code: 1
- duration_ms: 180
- row_count: n/a
- error: ERROR 2005 (HY000): Unknown MySQL server host 'localhost --port=3307 --user=ai_investigator --database=crypto_ai --batch --raw --comments --default' (11001)

- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-B01
- timestamp: 2026-10-10 00:08:18
- requesting_agent: COORDINATOR
- run_id: db-connect-validate-20261010
- query_id: DBQ-B01
- purpose: TEST-B
- sql_file: DBQ-B01-COORDINATOR.sql
- result_file: DBQ-B01-COORDINATOR-result.txt
- blocked_by_wrapper: no
- exit_code: 1
- duration_ms: 182
- row_count: n/a
- error: ERROR 2005 (HY000): Unknown MySQL server host 'localhost --port=3307 --user=ai_investigator --database=crypto_ai --batch --raw --comments --default' (11001)

- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-C01
- timestamp: 2026-10-10 00:08:19
- requesting_agent: COORDINATOR
- run_id: db-connect-validate-20261010
- query_id: DBQ-C01
- purpose: TEST-C
- sql_file: DBQ-C01-COORDINATOR.sql
- result_file: DBQ-C01-COORDINATOR-result.txt
- blocked_by_wrapper: no
- exit_code: 1
- duration_ms: 145
- row_count: n/a
- error: ERROR 2005 (HY000): Unknown MySQL server host 'localhost --port=3307 --user=ai_investigator --database=crypto_ai --batch --raw --comments --default' (11001)

- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-D01
- timestamp: 2026-10-10 00:08:20
- requesting_agent: COORDINATOR
- run_id: db-connect-validate-20261010
- query_id: DBQ-D01
- purpose: TEST-D
- sql_file: DBQ-D01-COORDINATOR.sql
- result_file: DBQ-D01-COORDINATOR-result.txt
- blocked_by_wrapper: yes
- exit_code: 2
- duration_ms: 14
- row_count: n/a
- error: Rejected statement family 'CREATE'. Allowed: SELECT, SHOW, DESCRIBE, DESC, EXPLAIN.
- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-F01
- timestamp: 2026-10-10 00:08:28
- requesting_agent: CODEX
- run_id: db-connect-validate-20261010
- query_id: DBQ-F01
- purpose: TEST-F-codex-cycle
- sql_file: DBQ-F01-CODEX.sql
- result_file: DBQ-F01-CODEX-result.txt
- blocked_by_wrapper: no
- exit_code: 1
- duration_ms: 129
- row_count: n/a
- error: ERROR 2005 (HY000): Unknown MySQL server host 'localhost --port=3307 --user=ai_investigator --database=crypto_ai --batch --raw --comments --default' (11001)

- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

## DBQ-G01
- timestamp: 2026-10-10 00:08:43
- requesting_agent: CLAUDE
- run_id: db-connect-validate-20261010
- query_id: DBQ-G01
- purpose: TEST-G-claude-cycle
- sql_file: DBQ-G01-CLAUDE.sql
- result_file: DBQ-G01-CLAUDE-result.txt
- blocked_by_wrapper: no
- exit_code: 1
- duration_ms: 164
- row_count: n/a
- error: ERROR 2005 (HY000): Unknown MySQL server host 'localhost --port=3307 --user=ai_investigator --database=crypto_ai --batch --raw --comments --default' (11001)

- database: crypto_ai
- host: localhost
- port: 3307
- user: ai_investigator
- timestamp_policy: raw MySQL timestamps are UTC; never label raw values as KSA; display UTC then KSA=UTC+3h when converting

