package com.crypto.execution.service;

import com.crypto.domain.Candle;
import com.crypto.domain.TradeSignal;
import com.crypto.repository.CandleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecoveryTransitionServiceTest {
    @Mock CandleRepository candleRepository;

    @Test
    void enaRegressionUsesOnlyCandlesClosedBeforeSignalAndDetectsAbsorptionRecovery() {
        RecoveryTransitionService service = new RecoveryTransitionService(candleRepository);
        Instant generatedAt = Instant.parse("2026-08-20T10:04:51Z");
        TradeSignal signal = new TradeSignal();
        signal.setSymbol("ENAUSDT");
        signal.setGeneratedAt(generatedAt);

        List<Candle> candles = new ArrayList<>();
        candles.add(candle("2026-08-20T09:55:00Z", "0.0963", "341392", 0.6336));
        candles.add(candle("2026-08-20T09:56:00Z", "0.0963", "153086", 0.8890));
        candles.add(candle("2026-08-20T09:57:00Z", "0.0963", "135766", 0.9521));
        candles.add(candle("2026-08-20T09:58:00Z", "0.0968", "68362", 0.4907));
        candles.add(candle("2026-08-20T09:59:00Z", "0.0967", "43355", 0.1292));
        candles.add(candle("2026-08-20T10:00:00Z", "0.0966", "93971", 0.0747));
        candles.add(candle("2026-08-20T10:01:00Z", "0.0967", "50956", 0.8526));
        candles.add(candle("2026-08-20T10:02:00Z", "0.0969", "17458", 0.8747));
        candles.add(candle("2026-08-20T10:03:00Z", "0.0971", "48351", 0.7391));
        // Repository returns descending, exactly like production query.
        List<Candle> descending = new ArrayList<>(candles);
        java.util.Collections.reverse(descending);
        when(candleRepository.findClosedCandlesClosedAtOrBefore(eq("ENAUSDT"), eq("1m"), eq(generatedAt), any(Pageable.class)))
                .thenReturn(descending);

        var result = service.evaluate(signal);

        assertThat(result.probeReady()).isTrue();
        assertThat(result.explanation()).contains("85.26%/87.47%/73.91%");
        assertThat(result.explanation()).contains("pullback/test");
    }

    @Test
    void doesNotTriggerWhenLatestClosedCandlesDoNotShowPersistentBuyerRecovery() {
        RecoveryTransitionService service = new RecoveryTransitionService(candleRepository);
        Instant generatedAt = Instant.parse("2026-08-20T10:04:51Z");
        TradeSignal signal = new TradeSignal();
        signal.setSymbol("ENAUSDT");
        signal.setGeneratedAt(generatedAt);
        List<Candle> descending = List.of(
                candle("2026-08-20T10:03:00Z", "0.0971", "48000", 0.51),
                candle("2026-08-20T10:02:00Z", "0.0969", "17000", 0.55),
                candle("2026-08-20T10:01:00Z", "0.0967", "50000", 0.60),
                candle("2026-08-20T10:00:00Z", "0.0966", "93000", 0.07),
                candle("2026-08-20T09:59:00Z", "0.0967", "43000", 0.13),
                candle("2026-08-20T09:58:00Z", "0.0968", "68000", 0.49),
                candle("2026-08-20T09:57:00Z", "0.0963", "130000", 0.95),
                candle("2026-08-20T09:56:00Z", "0.0963", "150000", 0.89));
        when(candleRepository.findClosedCandlesClosedAtOrBefore(eq("ENAUSDT"), eq("1m"), eq(generatedAt), any(Pageable.class)))
                .thenReturn(descending);

        assertThat(service.evaluate(signal).probeReady()).isFalse();
    }

    private Candle candle(String open, String close, String volume, double buyRatio) {
        Instant openTime = Instant.parse(open);
        BigDecimal vol = new BigDecimal(volume);
        Candle c = new Candle();
        c.setSymbol("ENAUSDT");
        c.setIntervalCode("1m");
        c.setOpenTime(openTime);
        c.setCloseTime(openTime.plusSeconds(59));
        c.setOpenPrice(new BigDecimal(close));
        c.setHighPrice(new BigDecimal(close));
        c.setLowPrice(new BigDecimal(close));
        c.setClosePrice(new BigDecimal(close));
        c.setVolume(vol);
        c.setTakerBuyBaseVolume(vol.multiply(BigDecimal.valueOf(buyRatio)));
        c.setClosed(true);
        return c;
    }
}
