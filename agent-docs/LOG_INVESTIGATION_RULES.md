# Log investigation rules

Agents may READ repository-local logs only. Do not modify logs.

## Targeting
Search with identifiers such as:

- trade ID
- signal ID
- symbol
- interval
- candle timestamp
- replay/test ID
- correlation identifier

Do not load giant log files into an agent prompt. Use targeted `Select-String`/rg and read only the matching window plus limited context.

## Time
Log timestamps must be interpreted according to their source.
Raw MySQL/Binance timestamps in logs are UTC unless the log line itself states otherwise.
Never label a raw UTC value as KSA. When converting: UTC first, then KSA = UTC+3h.

## Secrets
Do not copy credentials, tokens, or `AGENT_DB_PASSWORD` from logs into reports.
If a secret appears, redact it and do not repeat it.
