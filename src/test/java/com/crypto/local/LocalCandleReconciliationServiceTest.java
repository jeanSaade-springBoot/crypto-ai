package com.crypto.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.client.binance.BinanceMarketDataClient;
import com.crypto.client.binance.dto.BinanceKline;
import com.crypto.client.config.binance.BinanceMarketDataProperties;
import com.crypto.repository.CandleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** Deterministic contract tests for the token-free FIX-153 repair path. */
class LocalCandleReconciliationServiceTest {
    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:h2:mem:reconciliation_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
    private final CoinConfigurationService coins = mock(CoinConfigurationService.class);
    private final BinanceMarketDataClient binance = mock(BinanceMarketDataClient.class);
    private final CandleRepository candles = mock(CandleRepository.class);
    private LocalReconciliationAuditRepository audit;
    private CandleAnalysisOutcomeStore outcomes;
    private LocalCandleReconciliationService service;
    private final Instant now = Instant.parse("2026-10-09T10:00:00Z");

    @BeforeEach void setUp() {
        jdbc.execute("CREATE TABLE candle(symbol VARCHAR(30),interval_code VARCHAR(10),open_time TIMESTAMP(6),close_time TIMESTAMP(6),closed BOOLEAN,ingestion_source VARCHAR(16))");
        jdbc.execute("CREATE TABLE local_reconciliation_audit(id BIGINT AUTO_INCREMENT PRIMARY KEY,subject_type VARCHAR(32),subject_key VARCHAR(160),symbol VARCHAR(30),interval_code VARCHAR(10),candle_open_time TIMESTAMP(6),signal_id BIGINT,status VARCHAR(48),failure_reason VARCHAR(128),attempt_count INT,next_attempt_at TIMESTAMP(6),exchange_time_snapshot TIMESTAMP(6),binance_open_time TIMESTAMP(6),binance_close_time TIMESTAMP(6),last_observation VARCHAR(64),late_live_close_at TIMESTAMP(6),resolved_at TIMESTAMP(6),created_at TIMESTAMP(6),updated_at TIMESTAMP(6),UNIQUE(subject_type,subject_key))");
        jdbc.execute("CREATE TABLE candle_analysis_outcome(id BIGINT AUTO_INCREMENT PRIMARY KEY,symbol VARCHAR(30),interval_code VARCHAR(10),candle_open_time TIMESTAMP(6),outcome VARCHAR(48),reason_code VARCHAR(128),signal_id BIGINT,origin VARCHAR(64),created_at TIMESTAMP(6),UNIQUE(symbol,interval_code,candle_open_time))");
        audit = new LocalReconciliationAuditRepository(jdbc);
        outcomes = new CandleAnalysisOutcomeStore(jdbc);
        BinanceMarketDataProperties properties = new BinanceMarketDataProperties();
        properties.setIntervals(List.of("1m"));
        when(coins.enabledSymbols()).thenReturn(List.of("BTCUSDT"));
        Executor direct = Runnable::run;
        service = new LocalCandleReconciliationService(jdbc, coins, properties, binance, candles, audit, outcomes,
            direct, new DataSourceTransactionManager(((DriverManagerDataSource) jdbc.getDataSource())));
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "mode", "OFF");
        ReflectionTestUtils.setField(service, "minimumCandles", 2);
        ReflectionTestUtils.setField(service, "requestBudget", 1);
        ReflectionTestUtils.setField(service, "liveCloseGraceSeconds", 10L);
        ReflectionTestUtils.setField(service, "safetyMarginSeconds", 2L);
        when(binance.getServerTime()).thenReturn(now);
    }

    @Test void healthyLateLiveCloseWithoutSuccessorOrReconnectIsLeftUntouched() {
        when(coins.enabledSymbols()).thenReturn(List.of()); // isolate the open-candle route from gap discovery
        Instant open = now.minusSeconds(120);
        openRow(open);
        service.scheduledScan();
        verify(binance, never()).getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class));
        verifyNoInteractions(candles);
    }

    @Test void successorEvidenceRepairsAnInterruptedOpenCandleAndRecordsNonLiveOutcome() {
        when(coins.enabledSymbols()).thenReturn(List.of()); // isolate the open-candle route from gap discovery
        Instant open = now.minusSeconds(120);
        openRow(open);
        candleRow(open.plusSeconds(60));
        BinanceKline kline = kline(open);
        when(binance.getKlines("BTCUSDT", "1m", open, open.plusSeconds(60).minusMillis(1))).thenReturn(List.of(kline));
        when(candles.finalizeExistingOpenFromAuthoritative(eq("BTCUSDT"), eq("1m"), eq(open), eq(kline.closeTime()),
            any(), any(), any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(true);

        service.scheduledScan();

        verify(candles).finalizeExistingOpenFromAuthoritative(eq("BTCUSDT"), eq("1m"), eq(open), eq(kline.closeTime()),
            any(), any(), any(), any(), any(), any(), anyLong(), any(), any());
        assertEquals("RECOVERED", audit.findCandle("BTCUSDT", "1m", open).orElseThrow().status());
        assertEquals("SKIPPED_NOT_LIVE", jdbc.queryForObject("SELECT outcome FROM candle_analysis_outcome", String.class));
    }

    @Test void reconnectEvidenceRepairsAnInterruptedOpenCandleWithoutSuccessor() {
        when(coins.enabledSymbols()).thenReturn(List.of());
        Instant open = now.minusSeconds(120);
        openRow(open);
        BinanceKline kline = kline(open);
        when(binance.getKlines("BTCUSDT", "1m", open, open.plusSeconds(60).minusMillis(1))).thenReturn(List.of(kline));
        when(candles.finalizeExistingOpenFromAuthoritative(eq("BTCUSDT"), eq("1m"), eq(open), eq(kline.closeTime()),
            any(), any(), any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(true);

        service.requestAfterReconnect(7L);

        assertEquals("RECOVERED", audit.findCandle("BTCUSDT", "1m", open).orElseThrow().status());
        verify(candles).finalizeExistingOpenFromAuthoritative(eq("BTCUSDT"), eq("1m"), eq(open), eq(kline.closeTime()),
            any(), any(), any(), any(), any(), any(), anyLong(), any(), any());
    }

    @Test void authoritativeCloseTimeReplacesAnIncorrectOpenRowCloseTime() {
        when(coins.enabledSymbols()).thenReturn(List.of());
        Instant open = now.minusSeconds(120);
        jdbc.update("INSERT INTO candle VALUES(?,?,?,?,?,?)", "BTCUSDT", "1m", java.sql.Timestamp.from(open),
            java.sql.Timestamp.from(open.plusSeconds(17)), false, "RUNTIME");
        candleRow(open.plusSeconds(60));
        BinanceKline kline = kline(open);
        when(binance.getKlines("BTCUSDT", "1m", open, open.plusSeconds(60).minusMillis(1))).thenReturn(List.of(kline));
        when(candles.finalizeExistingOpenFromAuthoritative(eq("BTCUSDT"), eq("1m"), eq(open), eq(kline.closeTime()),
            any(), any(), any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(true);

        service.scheduledScan();

        verify(candles).finalizeExistingOpenFromAuthoritative(eq("BTCUSDT"), eq("1m"), eq(open),
            eq(open.plusSeconds(60).minusMillis(1)), any(), any(), any(), any(), any(), any(), anyLong(), any(), any());
    }

    @Test void requestBudgetLimitsRecoveryRestCallsPerPass() {
        when(coins.enabledSymbols()).thenReturn(List.of());
        Instant first = now.minusSeconds(180), second = now.minusSeconds(120);
        openRow(first); candleRow(first.plusSeconds(60));
        openRow(second); candleRow(second.plusSeconds(60));
        BinanceKline kline = kline(first);
        when(binance.getKlines("BTCUSDT", "1m", first, first.plusSeconds(60).minusMillis(1))).thenReturn(List.of(kline));
        when(candles.finalizeExistingOpenFromAuthoritative(eq("BTCUSDT"), eq("1m"), eq(first), eq(kline.closeTime()),
            any(), any(), any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(true);

        service.scheduledScan();

        verify(binance, times(1)).getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class));
    }

    @Test void gapInsertRaceRecordsAlreadyPresentWithoutCreatingOutcome() {
        Instant open = now.minusSeconds(120);
        BinanceKline kline = kline(open);
        when(binance.getKlines("BTCUSDT", "1m", open, open.plusSeconds(60).minusMillis(1))).thenReturn(List.of(kline));
        when(candles.insertAuthoritativeGapBackfill(eq("BTCUSDT"), eq("1m"), eq(open), eq(kline.closeTime()),
            any(), any(), any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(false);

        service.scheduledScan();

        assertEquals("ALREADY_PRESENT", audit.findCandle("BTCUSDT", "1m", open).orElseThrow().status());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM candle_analysis_outcome", Integer.class));
    }

    @Test void confirmedExchangeGapBecomesExplicitUnavailableOutcome() {
        Instant open = now.minusSeconds(120);
        when(binance.getKlines("BTCUSDT", "1m", open, open.plusSeconds(60).minusMillis(1))).thenReturn(List.of());
        when(binance.getKlinesAtOrAfter("BTCUSDT", "1m", open)).thenReturn(List.of(kline(open.plusSeconds(60))));

        service.scheduledScan();

        assertEquals("EXPIRED", audit.findCandle("BTCUSDT", "1m", open).orElseThrow().status());
        assertEquals("SKIPPED_DATA_UNAVAILABLE", jdbc.queryForObject("SELECT outcome FROM candle_analysis_outcome", String.class));
    }

    @Test void drainPreventsAnyRestAdmission() {
        LocalDrainService drain = mock(LocalDrainService.class);
        when(drain.beginReconciliation()).thenReturn(false);
        ReflectionTestUtils.setField(service, "drain", drain);
        service.scheduledScan();
        verifyNoInteractions(binance);
    }

    private void openRow(Instant open) {
        jdbc.update("INSERT INTO candle VALUES(?,?,?,?,?,?)", "BTCUSDT", "1m", java.sql.Timestamp.from(open), java.sql.Timestamp.from(open.plusSeconds(59)), false, "RUNTIME");
    }
    private void candleRow(Instant open) {
        jdbc.update("INSERT INTO candle VALUES(?,?,?,?,?,?)", "BTCUSDT", "1m", java.sql.Timestamp.from(open), java.sql.Timestamp.from(open.plusSeconds(59)), true, "RUNTIME");
    }
    private BinanceKline kline(Instant open) {
        BigDecimal one = BigDecimal.ONE;
        return new BinanceKline(open, one, one, one, one, one, open.plusSeconds(60).minusMillis(1), one, 1, one, one);
    }
}
