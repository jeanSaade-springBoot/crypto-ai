package com.crypto.local;

import org.springframework.dao.DataAccessException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Fails boot clearly if code that records durable candle outcomes is deployed
 * without its Flyway table. Flyway completes before ApplicationRunner runs. */
@Component
public class CandleAnalysisOutcomeSchemaVerifier implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public CandleAnalysisOutcomeSchemaVerifier(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void run(ApplicationArguments args) {
        try {
            jdbc.queryForList("SELECT 1 FROM candle_analysis_outcome WHERE 1=0");
        } catch (DataAccessException missing) {
            throw new IllegalStateException(
                "Required table candle_analysis_outcome is missing. Apply Flyway V108 before starting outcome-writing signal processing.",
                missing);
        }
    }
}
