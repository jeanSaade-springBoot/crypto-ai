Jean direction (2026-10-11): Replay must match Production business logic so a simulated trade produces the same decision/exit output. Target 98-99% similarity is acceptable; 100% is not required where wallet/quantity/timing are declared non-comparable.

FIX-126/FIX-156 intent: Replay uses Replay-owned output tables only as the final output. Production uses the wallet/paper account tables. Replay must not read or write wallet_trade, paper_position, wallet_settings, or other account state during a run.

Agents must walk CURRENT workspace source (not only the zip hashes in the draft) and confirm whether RPR-0.9.1 / the text in FINAL_CHANGE_SPECIFICATION_RPR-0.9.1.md is complete and true against the code. Header currently labels itself RPR-0.9.4-DRAFT inside a 0.9.1 filename; resolve that.

No implementation. No Production code change. No Git. Dual-agent independent Round 1, then evidence-backed challenge. VERDICT APPROVED only if the exact spec is complete and safe to freeze for later user authorization; otherwise REJECTED with missing code paths and register gaps.
