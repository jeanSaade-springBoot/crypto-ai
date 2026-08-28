# Position Management — Shadow Mode

This update adds a read-only advisory engine after signal generation.

## Hard safety boundary

The new service does not:

- change AnalysisService or FinalDecisionService;
- change BUY/WATCH/NEUTRAL/SELL decisions;
- execute BUY or SELL;
- update wallet balances;
- close positions;
- alter stop-loss or take-profit.

It only analyzes an already-open `wallet_managed_position` when a new signal for that symbol arrives and stores:

- HOLD, REDUCE, or EXIT recommendation;
- exit pressure score (0–25);
- confidence;
- unrealized P/L;
- holding time;
- component scores and evidence.

API: `GET /api/position-management/latest`

Database migration: `V31__create_position_analysis.sql`
