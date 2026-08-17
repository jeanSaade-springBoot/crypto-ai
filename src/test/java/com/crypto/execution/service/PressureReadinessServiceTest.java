package com.crypto.execution.service;

import com.crypto.domain.Candle;
import com.crypto.domain.TradeSignal;
import com.crypto.repository.CandleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression protection for the exact production PressureReadinessService.
 * No test-only pressure algorithm is copied here: fixtures are fed into the production
 * service and assertions verify the same thresholds used by live and Proven replay.
 */
class PressureReadinessServiceTest {

    @Test
    void shibSequenceDetectsRecentBullishReleaseThenSellAbsorption() {
        CandleRepository repository = mock(CandleRepository.class);
        PressureReadinessService service = new PressureReadinessService(repository);

        List<Candle> candles = new ArrayList<>();
        Instant start = Instant.parse("2026-08-17T12:20:00Z");

        // Six quiet 5m buckets establish the prior 30m high at 0.00000445.
        for (int minute = 0; minute < 30; minute++) {
            candles.add(candle(start.plusSeconds(minute * 60L), "0.00000444", "0.00000445",
                    "0.00000443", "0.00000444", "100", "50"));
        }

        // 12:50 bucket: pressure builds near the old high.
        Instant t1250 = Instant.parse("2026-08-17T12:50:00Z");
        for (int i = 0; i < 5; i++) {
            candles.add(candle(t1250.plusSeconds(i * 60L), "0.00000444", "0.00000445",
                    "0.00000444", "0.00000445", "100", "87"));
        }

        // 12:55 bucket: analyzed SHIB-style bullish release through the prior high.
        Instant t1255 = Instant.parse("2026-08-17T12:55:00Z");
        for (int i = 0; i < 5; i++) {
            candles.add(candle(t1255.plusSeconds(i * 60L), "0.00000445", "0.00000446",
                    "0.00000444", "0.00000446", "120", "119"));
        }

        // 13:00 partial bucket: heavy aggressive selling but only a small giveback.
        Instant t1300 = Instant.parse("2026-08-17T13:00:00Z");
        for (int i = 0; i < 4; i++) {
            candles.add(candle(t1300.plusSeconds(i * 60L), "0.00000446", "0.00000447",
                    "0.00000445", "0.00000445", "500", "35"));
        }

        candles.sort(Comparator.comparing(Candle::getOpenTime).reversed());
        when(repository.findClosedCandlesAtOrBefore(eq("SHIBUSDT"), eq("1m"), any(Instant.class), any(Pageable.class)))
                .thenReturn(candles);

        TradeSignal current = TradeSignal.builder()
                .symbol("SHIBUSDT")
                .interval("1m")
                .candleOpenTime(Instant.parse("2026-08-17T13:03:00Z"))
                .generatedAt(Instant.parse("2026-08-17T13:04:44Z"))
                .build();

        PressureReadinessService.Result result = service.evaluate(current);

        assertThat(result.recentBullishRelease()).isTrue();
        assertThat(result.recentBullishReleaseAt()).isEqualTo(Instant.parse("2026-08-17T12:55:00Z"));
        assertThat(result.state()).isEqualTo(PressureReadinessService.State.SELL_ABSORPTION);
        assertThat(result.explanation()).contains("recent bullish release");
    }

    private Candle candle(Instant openTime, String open, String high, String low, String close,
                          String volume, String takerBuy) {
        return Candle.builder()
                .symbol("SHIBUSDT")
                .intervalCode("1m")
                .openTime(openTime)
                .closeTime(openTime.plusSeconds(59))
                .openPrice(new BigDecimal(open))
                .highPrice(new BigDecimal(high))
                .lowPrice(new BigDecimal(low))
                .closePrice(new BigDecimal(close))
                .volume(new BigDecimal(volume))
                .takerBuyBaseVolume(new BigDecimal(takerBuy))
                .closed(true)
                .build();
    }
}
