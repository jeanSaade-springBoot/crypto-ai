STUDY WINDOW: WINDOW1
SNAPSHOT TIME UTC: 2026-10-10 20:51:50
SNAPSHOT TIME KSA: 2026-10-11 00:51:47
INTENDED WINDOW CLOCK KSA: 2026-10-11 10:00:00
SNAPSHOT NOTE: TEST MODE: snapshot is launcher start, not the scheduled 2026-10-11 10:00:00 KSA clock.
AUTHORITATIVE ENTITY: crypto_ai.paper_position
SELECTION: status=CLOSED AND closed_at IS NOT NULL AND realized_pnl < 0 ORDER BY closed_at DESC LIMIT 6
EXCLUDED: OPEN/incomplete rows; NULL closed_at; non-negative realized_pnl; wallet_position_test (Replay)

SELECTED TRADE IDS:
1. 96
2. 91
3. 82
4. 78
5. 71
6. 72

PROOF QUERIES: DBQ-FTS-001 .. DBQ-FTS-005