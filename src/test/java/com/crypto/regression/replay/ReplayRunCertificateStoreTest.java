package com.crypto.regression.replay;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReplayRunCertificateStoreTest {
    @Test
    void writesOnlyFailClosedNotMeasuredCertificate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReplayPolicyProfile profile = new ReplayPolicyProfile(9L, "profile-hash", Instant.EPOCH,
                "fingerprint", "BALANCED", true, BigDecimal.ONE, BigDecimal.TEN, 1,
                "fixture", BigDecimal.ONE, false, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN);

        new ReplayRunCertificateStore(jdbc).recordNotMeasured(9L, profile, "QUALIFIED_EVIDENCE_MISSING");

        verify(jdbc).update(contains("'NOT_MEASURED'"), eq(9L), eq("profile-hash"),
                contains("QUALIFIED_EVIDENCE_MISSING"));
    }
}
