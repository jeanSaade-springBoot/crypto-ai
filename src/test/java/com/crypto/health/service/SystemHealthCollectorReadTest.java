package com.crypto.health.service;

import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.shared.SharedMarketSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SystemHealthCollectorReadTest {
    @Test
    void collectorCountsAndFreshnessDoNotReadLocalCandles() {
        JdbcTemplate local = database();
        JdbcTemplate collector = database();
        CoinConfigurationService coins = mock(CoinConfigurationService.class);
        when(coins.enabledSymbols()).thenReturn(List.of("BTCUSDT"));
        SystemHealthDailyService service = new SystemHealthDailyService(local, coins);
        SharedMarketSource source = mock(SharedMarketSource.class);
        when(source.reader()).thenReturn(collector);
        ReflectionTestUtils.setField(service, "sharedCandleSource", source);
        Instant now = Instant.parse("2026-09-29T21:28:00Z");
        insert(collector, "BTCUSDT", "1m", now.minusSeconds(120), now.minusSeconds(60), 1);
        insert(collector, "BTCUSDT", "1m", now.minusSeconds(60), now, 0);
        insert(collector, "OTHER", "1m", now.minusSeconds(120), now.minusSeconds(60), 1);
        insert(collector, "BTCUSDT", "5m", now.minusSeconds(18000), now.minusSeconds(17700), 1);
        assertEquals(1L, service.candleCounts(now.minusSeconds(3600), now).get("1m"));
        var rows = service.candleStaleness(now, Set.of("BTCUSDT"));
        var minute = rows.stream().filter(r -> "1m".equals(r.get("interval"))).findFirst().orElseThrow();
        assertEquals(now.minusSeconds(60), minute.get("lastAt"));
        assertEquals("OK", minute.get("status"));
        assertEquals(2, rows.stream().filter(r -> "CRITICAL".equals(r.get("status"))).count());
        // OFF uses local reader through SharedMarketSource; routing must remain supported.
        when(source.reader()).thenReturn(local);
        assertEquals(0L, service.candleCounts(now.minusSeconds(3600), now).get("1m"));
    }

    private JdbcTemplate database() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE candle(symbol VARCHAR(30), interval_code VARCHAR(10), open_time TIMESTAMP, close_time TIMESTAMP, closed INT)");
        return jdbc;
    }
    private void insert(JdbcTemplate jdbc, String symbol, String interval, Instant open, Instant close, int closed) {
        jdbc.update("INSERT INTO candle VALUES(?,?,?,?,?)", symbol, interval, Timestamp.from(open), Timestamp.from(close), closed);
    }
}
