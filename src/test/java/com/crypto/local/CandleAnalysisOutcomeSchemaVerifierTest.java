package com.crypto.local;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CandleAnalysisOutcomeSchemaVerifierTest {
    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:outcome_schema_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
    }

    @Test void startupFailsClearlyWhenOutcomeTableIsMissing() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> new CandleAnalysisOutcomeSchemaVerifier(jdbc()).run(new DefaultApplicationArguments()));
        org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("candle_analysis_outcome"));
    }

    @Test void startupAllowsOutcomeWritingRuntimeWhenTableExists() {
        JdbcTemplate jdbc = jdbc();
        jdbc.execute("CREATE TABLE candle_analysis_outcome(id BIGINT AUTO_INCREMENT PRIMARY KEY)");
        assertDoesNotThrow(() -> new CandleAnalysisOutcomeSchemaVerifier(jdbc).run(new DefaultApplicationArguments()));
    }
}
