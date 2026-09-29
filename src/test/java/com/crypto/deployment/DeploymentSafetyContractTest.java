package com.crypto.deployment;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

/** FIX-135: source contract checks, NOT Windows process/PowerShell integration tests. */
class DeploymentSafetyContractTest {
    @Test void automaticBuildRunsTestsBeforeDeployment() throws Exception {
        String pipeline=Files.readString(Path.of("Jenkinsfile"));
        assertFalse(pipeline.contains("parameters {"));
        assertFalse(pipeline.contains("params."));
        assertFalse(pipeline.contains("when {"));
        // New pushes supersede the prior build; the host mutex serializes deployment.
        assertTrue(java.util.regex.Pattern.compile(
                "disableConcurrentBuilds\\s*\\(\\s*abortPrevious\\s*:\\s*true\\s*\\)")
                .matcher(pipeline).find(), "New builds must abort the previous pipeline");
        assertFalse(pipeline.contains("-DskipTests"));
        int build = pipeline.indexOf("mvn -B -ntp clean package");
        int deploy = pipeline.indexOf("stage('Deploy Trader LIVE')");
        assertTrue(build >= 0 && deploy > build);
    }
    @Test void deploymentPreservesSafeguardsAndRequestsApprovedLive() throws Exception {
        String script=Files.readString(Path.of("scripts/deploy/Deploy-Trader.ps1"));
        assertFalse(script.contains("Remove-Item"));
        assertFalse(script.contains("BACKUP_JAR"));
        assertTrue(script.contains("Copy-Item -LiteralPath $sourceJar -Destination $releaseJar"));
        assertTrue(script.contains("--shared-market.mode=LIVE"));
        assertTrue(script.contains("--shared-market.activation-approved=true"));
        assertTrue(script.contains("$listener.OwningProcess -ne $child.Id"));
        assertTrue(script.contains("NO rollback or second launch"));
    }
    @Test void incidentMigrationIsNotSilentlyRewritten() throws Exception {
        byte[] bytes=Files.readString(Path.of("src/main/resources/db/migration/V89__fix_132_shared_market_delivery.sql")).replace("\r\n", "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("1c4ae0ec3024548d38340d3ed10bccca0aab82b60b7e395ef5308ec0280d203e", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
}
