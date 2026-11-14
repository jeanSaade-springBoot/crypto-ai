package com.crypto.regression.replay;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReplayRunOutputStoreTest {
    @Test
    void recordsReplayOwnedNonParityClaimedExecutionSummary() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new ReplayRunOutputStore(jdbc).recordExecutionSummary(17L, 2, 1, 1,
                new BigDecimal("1.25"), new BigDecimal("10001.25"),
                "EXACT_PRICE_REPLAY", "PRODUCTION_PARITY");

        verify(jdbc).update(contains("NOT_PARITY_CLAIMED"), eq(17L),
                contains("EXACT_PRICE_REPLAY"));
    }
}
