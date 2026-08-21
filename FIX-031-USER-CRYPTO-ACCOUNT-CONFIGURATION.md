# FIX-031 — Logged-in-user Crypto Account configuration

## Goal
Prepare the application for a later Binance LIVE_MICRO execution adapter without changing the proven trading logic. Each authenticated application user owns an independent exchange-account configuration.

## User boundary
Ownership is resolved from Spring Security `Principal.getName()` -> `app_user.id`. The browser never sends a user id, so one session cannot select another user's crypto account.

## Stored per user
- Exchange (`BINANCE`)
- Account label
- Execution mode (`PAPER` or `LIVE_MICRO`)
- Maximum order USDT
- Maximum total exposure USDT
- Maximum open live positions
- Maximum daily live loss USDT
- Encrypted Binance API key/secret

## Credential protection
Credentials are AES-GCM encrypted before persistence. Configure `CRYPTO_ACCOUNT_MASTER_KEY` as a Base64 encoded 32-byte key. API responses never include the secret and show only a masked API-key hint. Withdrawals should remain disabled on the Binance API key.

## What does not change
No signal score, BUY/SELL rule, ATR, TP/SL, continuation, position-management, paper wallet, Production decision path or Replay behavior was modified. LIVE_MICRO is metadata/configuration only in FIX-031.

## Regression
Use two enabled `app_user` records, authenticate separately and verify each session receives a different `crypto_account_configuration` row. Changing one must not change the other. Credential save must require the master key and raw credentials must never appear in GET responses. Existing trading tests remain authoritative for behavior parity.
