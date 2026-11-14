package com.crypto.regression.replay;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReplayDecisionTraceStoreTest {
    @Test
    void recordsLocalBuyDecisionAsNotMeasuredEvidence() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new ReplayDecisionTraceStore(jdbc).recordBuyDecision(12L, 44L, Instant.EPOCH,
                new ReplayBuyValidationPolicy.Result(true, 25, "BUY_ALLOWED", "valid fixture signal"));

        verify(jdbc).update(contains("'NOT_MEASURED'"), eq(12L), eq("BUY-44"),
                eq(java.sql.Timestamp.from(Instant.EPOCH)), contains("BUY_ALLOWED"));
    }
}
