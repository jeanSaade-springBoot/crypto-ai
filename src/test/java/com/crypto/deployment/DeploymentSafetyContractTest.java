package com.crypto.deployment;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

/** FIX-135: source contract checks, NOT Windows process/PowerShell integration tests. */
class DeploymentSafetyContractTest {
    // FIX-153 source ZIP does not contain Jenkinsfile or deployment scripts. Verify the
    // shipped local contract here; Windows process and external pipeline acceptance remain release gates.
    @Test void shippedDefaultsUseLocalRuntimeAndDisableBothSharedSchedulers()throws Exception{
        String config=Files.readString(Path.of("src/main/resources/application.yml"));
        assertTrue(config.contains("mode: ${SHARED_MARKET_MODE:OFF}"));
        assertTrue(config.contains("enabled: ${SHARED_MARKET_FIX142_ENABLED:false}"));
        assertTrue(config.contains("acknowledgment-enabled: ${SHARED_MARKET_FIX142_ACK_ENABLED:false}"));
        assertTrue(config.contains("scheduled-analysis-enabled: false"));
        assertTrue(config.contains("instance-lock-path:"));
    }
    @Test void sharedRollbackRequiresExplicitRuntimeFlags()throws Exception{
        String source=Files.readString(Path.of("src/main/java/com/crypto/shared/SharedMarketConsumer.java"));
        assertTrue(source.contains("if(fix142Enabled && !source.enabled())throw"));
        String prices=Files.readString(Path.of("src/main/java/com/crypto/market/service/MarketPriceEventService.java"));
        assertTrue(prices.indexOf("if(\"OFF\".equals(mode))return;")<prices.indexOf("SELECT COUNT(*) FROM shared_market_cutover_history"));
    }
    @Test void incidentMigrationIsNotSilentlyRewritten() throws Exception {
        byte[] bytes=Files.readString(Path.of("src/main/resources/db/migration/V89__fix_132_shared_market_delivery.sql")).replace("\r\n", "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("1c4ae0ec3024548d38340d3ed10bccca0aab82b60b7e395ef5308ec0280d203e", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
}
