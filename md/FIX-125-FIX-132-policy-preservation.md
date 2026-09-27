# Shared business-policy preservation

Compared against pom(20260926-180106).zip, normalizing CRLF/LF only. These checks establish unchanged source, not historical outcome parity.

| File | Baseline vs candidate | SHA-256 (normalized) |
|---|---|---|
| src/main/java/com/crypto/wallet/service/WalletExecutionSizingPolicy.java | Identical | 3aa2d8471e2f5524cea857170144592e07cda214fe3e42c3e7b0b80481e988f9 |
| src/main/java/com/crypto/execution/service/ExecutionIntelligenceService.java | Identical | dece899ca98b0f47cab256f9d4646921c27d7186cfd9fe362322109ec1c11be9 |
| src/main/java/com/crypto/position/service/PositionExitPolicy.java | Identical | 457286cf181b9dd4e934b9c0bac0f32fa90e4669fb22080d7eb7035683261aff |
| src/main/java/com/crypto/position/service/PositionContinuationPolicy.java | Identical | cbbc78f014cd43cd56aab8113111a43697b84c7175d45b85abd5123b6ba1a916 |
| src/main/java/com/crypto/position/service/NearTpFailureProtectionPolicy.java | Identical | 4d2ec493c246922d4307fcd619929cdf819cf91771715fc487f08f2b85fafccc |
| src/main/java/com/crypto/position/service/PositionPriceAuthorityPolicy.java | Identical | 9fea335597eabe31e2539e49541942db5b5b2ad5e0f2c93192edd9578454950a |
| src/main/java/com/crypto/position/service/DynamicProfitLockService.java | Identical | 553cfc1ab8bb056ee2916271ec403f703b5eb00f759a8011db71762ac5ffe748 |
| src/main/java/com/crypto/execution/service/StopLossEvidencePolicy.java | Identical | dc9371d108dfa1e289ce4af19848df1e3cf8ca078bc9b3a609e9533724605f33 |
| src/main/java/com/crypto/regression/service/ShadowProductionReplayService.java | Identical | 5ef84716e74ef9928c49a818df3de6850782c1832b10564e8887806f81e46bf9 |
| src/main/java/com/crypto/execution/service/ExecutionReplayScope.java | Identical | 022f61c43f8e012ddf81fc2221ea7fb650e14c366d1dc4feb7e8a8dcdb6ecec4 |

Production wallet service edits are transaction/current-read changes documented separately. Replay never reads coordination tables. verifyEventResolution remains in the unchanged Replay engine. No Shadow-write optimization is included.

Acceptance still requires identical ordered prices, exact candle lineages and fingerprints, initial balances/positions, configuration, windows and rule versions. Differences in input availability, historical repairs or live delivery timing can change actual outcomes; this report does not hide them.
