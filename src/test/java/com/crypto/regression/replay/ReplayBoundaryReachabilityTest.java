package com.crypto.regression.replay;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** FIX-156 / T156-09a: keep baseline Replay orchestration out of wallet, HTTP and shared execution paths. */
class ReplayBoundaryReachabilityTest {
    @Test
    void shadowReplayDoesNotImportForbiddenRuntimeDependencies() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/crypto/regression/service/ShadowProductionReplayService.java"));
        assertFalse(source.contains("ExecutionIntelligenceService"));
        assertFalse(source.contains("ExecutionPriceAuthorityService"));
        assertFalse(source.contains("WalletSettingsRepository"));
        assertFalse(source.contains("BinanceMinimumExecutionPolicy"));
        assertFalse(source.contains("ExecutionOpportunity"));
        assertFalse(source.contains("wallet_position_test"));
        assertFalse(source.contains("wallet_execution_test"));
        assertFalse(source.contains("position_management_test"));
    }
}
