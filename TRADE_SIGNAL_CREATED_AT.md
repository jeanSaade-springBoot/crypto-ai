# Trade signal `created_at`

Flyway migration:

- `V4__add_created_at_to_trade_signal.sql`

The migration:

1. Adds `trade_signal.created_at`.
2. Backfills existing records from `generated_at`.
3. Makes the column non-null with a database default.
4. Adds `idx_trade_signal_created_at`.

`TradeSignal.createdAt` uses Hibernate `@CreationTimestamp`, so it is populated automatically and cannot be updated through JPA.

The previous sentiment provider migration was moved from V4 to V5 to avoid duplicate Flyway versions.
