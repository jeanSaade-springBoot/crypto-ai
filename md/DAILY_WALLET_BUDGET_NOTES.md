# Daily Wallet Budget

- Flyway migration: `V30__add_daily_wallet_budget.sql`
- Default maximum automatic BUY executions per day: `6`
- At the first approved BUY of the day:
  - `tradable USDT = available USDT - minimum reserve`
  - `fixed BUY budget = tradable USDT / maximum daily new positions`
- The budget stays fixed for the full application day.
- Every successful automatic BUY increments `executed_buys`.
- Automatic SELLs are always permitted for managed positions, do not increment the BUY counter, and do not recalculate the budget.
- The wallet and main dashboard show today's fixed budget and BUY progress.
