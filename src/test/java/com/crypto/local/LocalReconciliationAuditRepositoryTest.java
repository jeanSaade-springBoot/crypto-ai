package com.crypto.local;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class LocalReconciliationAuditRepositoryTest {
    private final JdbcTemplate jdbc=new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
    private final LocalReconciliationAuditRepository audit=new LocalReconciliationAuditRepository(jdbc);

    LocalReconciliationAuditRepositoryTest(){
        jdbc.execute("""
            CREATE TABLE local_reconciliation_audit(
              id BIGINT AUTO_INCREMENT PRIMARY KEY,subject_type VARCHAR(32),subject_key VARCHAR(160),symbol VARCHAR(30),
              interval_code VARCHAR(10),candle_open_time TIMESTAMP(6),signal_id BIGINT,status VARCHAR(48),failure_reason VARCHAR(128),
              attempt_count INT,next_attempt_at TIMESTAMP(6),exchange_time_snapshot TIMESTAMP(6),binance_open_time TIMESTAMP(6),
              binance_close_time TIMESTAMP(6),last_observation VARCHAR(64),late_live_close_at TIMESTAMP(6),resolved_at TIMESTAMP(6),
              created_at TIMESTAMP(6),updated_at TIMESTAMP(6),UNIQUE(subject_type,subject_key))
            """);
    }

    @Test void canonicalCandleKeyUsesFixedEpochMillisecondPrecision(){
        Instant value=Instant.ofEpochSecond(1_700_000_000L,123_999_999);
        assertEquals("ETHUSDT|1m|1700000000123",LocalReconciliationAuditRepository.candleKey("ETHUSDT","1m",value));
    }

    @Test void pendingIsDueAtItsScheduledTimeButTerminalStateNeverIs(){
        Instant open=Instant.parse("2026-10-09T07:30:00Z");
        Instant now=Instant.parse("2026-10-09T07:31:00Z");
        audit.pending("ETHUSDT","1m",open,1,now.plusSeconds(5),"REST_NOT_FINAL",now);
        assertFalse(audit.due("ETHUSDT","1m",open,now));
        assertTrue(audit.due("ETHUSDT","1m",open,now.plusSeconds(5)));
        audit.terminal("ETHUSDT","1m",open,"EXPIRED","NOT_ON_EXCHANGE",now,null,null);
        assertFalse(audit.due("ETHUSDT","1m",open,now.plusSeconds(600)));
    }
}
