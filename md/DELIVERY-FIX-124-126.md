# FIX-124 / FIX-126 candidate delivery

Baseline: latest crypto-ai-trader FIX-122 package including planned FIX-125, itself based on the approved FIX-120 baseline plus FIX-121. No crypto-ai-next code is included.

## Included changes

- FIX-124: candidate live-protection initial-deadlock recovery and transaction isolation. Persist input before protection, allow one initial-lock retry after rollback, retain input on failure, and show durable Production incidents in Proven / Analyze Trades. Broader analysis/recovery deadlocks remain open.
- FIX-126: nullable MEDIUMTEXT execution_message migration and matching WalletTrade mapping.
- Separate sequential patches under patches/. The complete source already contains BOTH; do not apply the patches again to this ZIP.
- FIX-123 sizing and FIX-125 wallet synchronization-through-commit remain planned and unimplemented.

## Actual checks

- Node: 11 tests passed, zero failures (existing FIX-121/FIX-122 plus new FIX-124 UI checks).
- JS syntax checks: Proven / Analyze Trades and fix registry succeeded.
- Java parser: 330 files, zero syntax errors. Parsing does NOT check imports, dependencies, type compatibility or compile the application.
- Maven invocation: `mvn -f fix124-126/pom.xml test` failed to start, exit 127, `mvn: command not found`. New JUnit tests are included but unrun. Authoring runtime has Java 17; project requires Java 21.
- Not run: MySQL migrations, live Spring/JPA concurrency tests, historical Replay comparison, full app build, deployment.

## Review and local verification

From the extracted project root, using Java 21 and Maven:

```bat
mvn test
```

Send the actual Maven result before treating this as a validated build. Validate V84 and V85 against a COPY of crypto_ai. Retain crypto-ai-next / crypto_ai_v2 separately.

FIX-124 changes transaction boundaries and failure recovery; it does not claim no possible execution impact. Previously failed checks may now execute, and market input commits before protection. See FIX-124.md for failure, crash-window, retry and ordering semantics. The precise trader lock cycle is not yet reproduced; do not label FIX-124 as a complete deadlock elimination.
