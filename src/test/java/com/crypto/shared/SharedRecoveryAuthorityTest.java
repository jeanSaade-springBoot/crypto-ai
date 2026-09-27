package com.crypto.shared;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SharedRecoveryAuthorityTest {
    @Test void liveRecoveryCannotStealDeliveryOrTradeWithoutABoundary() {
        var source=mock(SharedMarketSource.class);when(source.enabled()).thenReturn(true);
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE shared_market_consumer_state(symbol VARCHAR PRIMARY KEY,cutover_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE shared_market_event_delivery(symbol VARCHAR,interval_code VARCHAR,candle_open_time TIMESTAMP)");
        var gate=new SharedRecoveryAuthority(source,jdbc);Instant cutover=Instant.parse("2026-09-26T16:00:00Z");
        assertTrue(gate.deferCandle("BTCUSDT","1m",cutover,cutover.plusSeconds(60)));
        jdbc.update("INSERT INTO shared_market_consumer_state VALUES('BTCUSDT',?)",Timestamp.from(cutover));
        assertTrue(gate.deferCandle("BTCUSDT","1m",cutover,cutover.plusSeconds(60)));
        assertFalse(gate.deferCandle("BTCUSDT","1m",cutover.minusSeconds(120),cutover.minusSeconds(60)));
        jdbc.update("INSERT INTO shared_market_event_delivery VALUES('BTCUSDT','1m',?)",Timestamp.from(cutover.minusSeconds(120)));
        assertTrue(gate.deferCandle("BTCUSDT","1m",cutover.minusSeconds(120),cutover.minusSeconds(60)));
    }
    @Test void offModeAddsNoDeliveryQueryOrRestriction() {
        var source=mock(SharedMarketSource.class);var jdbc=mock(JdbcTemplate.class);
        var gate=new SharedRecoveryAuthority(source,jdbc);
        assertFalse(gate.ownsLiveExecution());assertFalse(gate.deferCandle("BTCUSDT","1m",Instant.EPOCH,Instant.EPOCH));
        verifyNoInteractions(jdbc);
    }
}
