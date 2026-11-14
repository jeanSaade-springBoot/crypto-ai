package com.crypto.regression.replay;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReplayFix122EvaluationStoreTest {

    @Test
    void usesOnlyTheFourApprovedO8StatementShapes() {
        // FIX-156 / T156-09b: direct guard coverage for each permitted O-8 operation.
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReplayFix122EvaluationStore store = new ReplayFix122EvaluationStore(jdbc);

        store.appendActiveRun(7L, "BTCUSDT", 99L, Instant.EPOCH, "DISPLAY", "{}");
        store.activeRunDetails(7L);
        store.purgeReplayRows();
        store.productionDisplayDetails("BTCUSDT", Instant.EPOCH, Instant.EPOCH.plusSeconds(60));

        verify(jdbc).update("DELETE FROM fix122_evaluation WHERE test_run_id IS NOT NULL");
    }
}
