package com.crypto.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps the reflection-based Replay schema and FIX-154 evidence migration coupled. */
class Fix154MigrationContractTest {
    @Test
    void v107AndForwardV109CreateProductionReplayAndAuditEvidenceColumns() throws Exception {
        String v107 = Files.readString(Path.of("src/main/resources/db/migration/V107__fix_154_trusted_higher_timeframe_buy.sql"));
        String v109 = Files.readString(Path.of("src/main/resources/db/migration/V109__fix_154_forward_replay_and_audit_columns.sql"));
        assertThat(v107).contains("ALTER TABLE trade_signal", "data_quality_valid")
                .contains("CREATE TABLE buy_htf_assessment_audit");
        assertThat(v109).contains("ALTER TABLE trade_signal_test", "btc_context_candle_close_time", "signal_mode")
                .contains("ALTER TABLE buy_htf_assessment_audit", "hard_confirmation_present");
    }
}
