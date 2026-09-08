package com.crypto.infrastructure.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

/** FIX-124: persist only recovery/failure incidents, not every price tick. These
 * Production observations are reporting data, never Replay decision inputs. */
@Service
public class Fix124ProtectionStore {
    private static final Logger log = LoggerFactory.getLogger(Fix124ProtectionStore.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    public Fix124ProtectionStore(DataSource source, PlatformTransactionManager manager) {
        jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(2);
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(2);
    }
    public void record(String symbol, String interval, Instant candleOpenTime, Instant observedAt,
                       BigDecimal price, int attempts, String outcome, RuntimeException failure) {
        try {
            transaction.executeWithoutResult(tx -> jdbc.update("""
                    INSERT INTO fix124_protection_incident
                    (symbol, interval_code, candle_open_time, observed_at, price, attempts, outcome, error_message)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, symbol, interval, Timestamp.from(candleOpenTime), Timestamp.from(observedAt),
                    price, attempts, outcome, failure == null ? null : failure.toString()));
        } catch (RuntimeException ex) {
            log.error("[FIX-124][DIAGNOSTIC_PERSIST_FAILED] symbol={}, observedAt={}, outcome={}", symbol, observedAt, outcome, ex);
        }
    }
}
