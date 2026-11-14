
## DBQ-010
- timestamp: 2026-10-10T18:40:49.5691018+03:00
- agent: COORDINATOR
- purpose: clock as ai_investigator
- SQL file: DBQ-010-COORDINATOR.sql
- exit code: 0
- duration_ms: 60
- approximate output lines: 1
- password: never logged


## DBQ-011
- timestamp: 2026-10-10T18:40:49.6891531+03:00
- agent: COORDINATOR
- purpose: clock as root read-only
- SQL file: DBQ-011-COORDINATOR.sql
- exit code: 0
- duration_ms: 74
- approximate output lines: 1
- password: never logged


## DBQ-012
- timestamp: 2026-10-10T18:41:21.3426563+03:00
- agent: COORDINATOR
- purpose: signal max generated_at by TF last 4h
- SQL file: DBQ-012-COORDINATOR.sql
- exit code: 0
- duration_ms: 146
- approximate output lines: 3
- password: never logged


## DBQ-013
- timestamp: 2026-10-10T18:41:21.4088529+03:00
- agent: COORDINATOR
- purpose: 1h last signal per symbol
- SQL file: DBQ-013-COORDINATOR.sql
- exit code: 0
- duration_ms: 42
- approximate output lines: 21
- password: never logged


## DBQ-014
- timestamp: 2026-10-10T18:41:22.5739228+03:00
- agent: COORDINATOR
- purpose: closed candle max close_time by TF last 4h
- SQL file: DBQ-014-COORDINATOR.sql
- exit code: 0
- duration_ms: 1152
- approximate output lines: 3
- password: never logged


## DBQ-016
- timestamp: 2026-10-10T18:42:29.5634082+03:00
- agent: COORDINATOR
- purpose: describe candle
- SQL file: DBQ-016-COORDINATOR.sql
- exit code: 0
- duration_ms: 40
- approximate output lines: 19
- password: never logged


## DBQ-017
- timestamp: 2026-10-10T18:42:30.3929893+03:00
- agent: COORDINATOR
- purpose: 1h closed candles around 18:00 KSA display
- SQL file: DBQ-017-COORDINATOR.sql
- exit code: 0
- duration_ms: 810
- approximate output lines: 21
- password: never logged


## DBQ-018
- timestamp: 2026-10-10T18:42:30.4476379+03:00
- agent: COORDINATOR
- purpose: 1h signal age using unix timestamps
- SQL file: DBQ-018-COORDINATOR.sql
- exit code: 0
- duration_ms: 46
- approximate output lines: 1
- password: never logged


## DBQ-019
- timestamp: 2026-10-10T18:50:38.1072283+03:00
- agent: COORDINATOR
- purpose: 1h close ingestion_source at 18:00 KSA hour
- SQL file: DBQ-019-COORDINATOR.sql
- exit code: 0
- duration_ms: 469
- approximate output lines: 1
- password: never logged


## DBQ-020
- timestamp: 2026-10-10T18:51:35.3547258+03:00
- agent: COORDINATOR
- purpose: monitor tick: latest 1h vs 1m/5m signal times vs NOW
- SQL file: DBQ-020-COORDINATOR.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 3
- password: never logged


## DBQ-021
- timestamp: 2026-10-10T18:52:37.8631626+03:00
- agent: COORDINATOR
- purpose: tick7: latest 1h vs 1m/5m ages
- SQL file: DBQ-021-COORDINATOR.sql
- exit code: 0
- duration_ms: 664
- approximate output lines: 3
- password: never logged


## DBQ-022
- timestamp: 2026-10-10T18:54:32.3947709+03:00
- agent: COORDINATOR
- purpose: tick8: 1h last generated_at
- SQL file: DBQ-022-COORDINATOR.sql
- exit code: 0
- duration_ms: 34
- approximate output lines: 1
- password: never logged


## DBQ-023
- timestamp: 2026-10-10T18:56:33.5676113+03:00
- agent: COORDINATOR
- purpose: tick9: 1h last generated_at
- SQL file: DBQ-023-COORDINATOR.sql
- exit code: 0
- duration_ms: 36
- approximate output lines: 1
- password: never logged


## DBQ-024
- timestamp: 2026-10-10T18:58:30.0353368+03:00
- agent: COORDINATOR
- purpose: tick10: 1h last generated_at
- SQL file: DBQ-024-COORDINATOR.sql
- exit code: 0
- duration_ms: 32
- approximate output lines: 1
- password: never logged


## DBQ-025
- timestamp: 2026-10-10T19:00:40.3511664+03:00
- agent: COORDINATOR
- purpose: tick11 19:00 KSA close: 1h vs 1m/5m ages and newest 1h rows
- SQL file: DBQ-025-COORDINATOR.sql
- exit code: 0
- duration_ms: 283
- approximate output lines: 3
- password: never logged


## DBQ-026
- timestamp: 2026-10-10T19:02:29.8636542+03:00
- agent: COORDINATOR
- purpose: tick12: confirm 1h still current after 19:00 close
- SQL file: DBQ-026-COORDINATOR.sql
- exit code: 0
- duration_ms: 42
- approximate output lines: 1
- password: never logged


## DBQ-027
- timestamp: 2026-10-10T19:05:42.4335274+03:00
- agent: COORDINATOR
- purpose: tick13: 1h last generated_at
- SQL file: DBQ-027-COORDINATOR.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 1
- password: never logged

