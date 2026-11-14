package com.crypto.regression.replay;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * FIX-156: fail-closed certificate writer.  This store records that parity was
 * not measured; it never upgrades a run to parity on its own.
 */
@Component
public class ReplayRunCertificateStore {
    private final JdbcTemplate jdbcTemplate;

    public ReplayRunCertificateStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordNotMeasured(long runId, ReplayPolicyProfile profile, String reason) {
        ReplayAccessPolicy.require("replay_run_certificate", ReplayAccessPolicy.Operation.REPLAY_INSERT);
        String safeReason = reason == null ? "UNSPECIFIED" : reason.replace("\\", "\\\\").replace("\"", "\\\"");
        jdbcTemplate.update("""
                INSERT INTO replay_run_certificate
                    (run_id, analysis_parity_status, lifecycle_parity_status, profile_hash, baseline_hash, certificate_json)
                VALUES (?, 'NOT_MEASURED', 'NOT_MEASURED', ?, 'UNAVAILABLE', CAST(? AS JSON))
                ON DUPLICATE KEY UPDATE analysis_parity_status=VALUES(analysis_parity_status),
                    lifecycle_parity_status=VALUES(lifecycle_parity_status), profile_hash=VALUES(profile_hash),
                    baseline_hash=VALUES(baseline_hash), certificate_json=VALUES(certificate_json)
                """, runId, profile.profileHash(), "{\"reason\":\"" + safeReason + "\"}");
    }
}
