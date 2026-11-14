FIX-149 corrected — trader crypto_ai, collector crypto_ai_v2

Canonical notes: src/main/resources/md/FIX-149.md

The first candidate failed because market_data_stream_event is NOT in crypto_ai.
This corrected V103 keeps trader Flyway history in crypto_ai but indexes the
confirmed crypto_ai_v2.market_data_stream_event on the same MySQL server.
Source-read URL/schema defaults are restored to crypto_ai_v2; trader DB unchanged.
V1–V102 are unchanged. No table copying or uncertain-protection release occurs.

Keep the trader stopped; merge/build this corrected source using Java 21.
Git Bash: mvn -B -ntp clean verify 2>&1 | tee fix149-corrected-build.log
PowerShell: mvn -B -ntp clean verify 2>&1 | Tee-Object -FilePath fix149-corrected-build.log

Before restart, save the failed V103 SELECT and run the guarded one-time script:
scripts/FIX-149-recover-failed-v103-local.sql
Its affected count must be exactly 1. It removes only this proven pre-DDL failed
entry from the confirmed local trader history; no successful history is removed.
Restart the corrected application and verify V103 success and source indexes.

Migration smoke checks pass; full Maven/real MySQL/load verification remains local.
