package com.crypto.service;

import com.crypto.domain.Candle;
import com.crypto.dto.IndicatorSnapshot;
import com.crypto.dto.TrendStructureResult;
import com.crypto.repository.CandleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrendStructureServiceTest {

    @Mock
    private CandleRepository candleRepository;

    @Test
    void shouldRewardImprovingHigherHighHigherLowStructure() {
        Instant start = Instant.parse("2026-08-04T10:00:00Z");
        List<Candle> candles = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            BigDecimal base = new BigDecimal("1854").add(BigDecimal.valueOf(index * 0.8));
            candles.add(candle(start.plusSeconds(index * 60L), base,
                    base.add(new BigDecimal("1.0")),
                    base.subtract(new BigDecimal("0.4")),
                    base.add(new BigDecimal("0.6")),
                    new BigDecimal("100")));
        }

        when(candleRepository.findTop200BySymbolAndIntervalCodeAndClosedTrueOrderByOpenTimeDesc("ETHUSDT", "1m"))
                .thenReturn(candles.reversed());

        TrendStructureService service = new TrendStructureService(candleRepository);
        TrendStructureResult result = service.evaluate(indicator(start.plusSeconds(9 * 60L)));

        assertTrue(result.score() >= 4);
        assertTrue(result.higherHigh());
        assertTrue(result.higherLow());
        assertTrue(result.continuationSupported());
    }

    @Test
    void shouldRemainConservativeWhenPriceStructureIsDeteriorating() {
        Instant start = Instant.parse("2026-08-04T10:00:00Z");
        List<Candle> candles = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            BigDecimal base = new BigDecimal("1870").subtract(BigDecimal.valueOf(index));
            candles.add(candle(start.plusSeconds(index * 60L), base,
                    base.add(new BigDecimal("0.3")),
                    base.subtract(new BigDecimal("1.2")),
                    base.subtract(new BigDecimal("0.8")),
                    new BigDecimal("150")));
        }

        when(candleRepository.findTop200BySymbolAndIntervalCodeAndClosedTrueOrderByOpenTimeDesc("ETHUSDT", "1m"))
                .thenReturn(candles.reversed());

        TrendStructureService service = new TrendStructureService(candleRepository);
        TrendStructureResult result = service.evaluate(indicator(start.plusSeconds(9 * 60L)));

        assertTrue(result.score() <= 2);
    }


    @Test
    void shouldRecognizeConfirmedBullishExpansionEvenWhenRsiIsHot() {
        Instant start = Instant.parse("2026-08-08T13:45:00Z");
        List<Candle> candles = new ArrayList<>();

        // Five-candle base followed by a five-candle expansion. The recent
        // block closes above the prior block high with stronger volume.
        for (int index = 0; index < 5; index++) {
            BigDecimal base = new BigDecimal("596.00").add(BigDecimal.valueOf(index * 0.08));
            candles.add(candle(start.plusSeconds(index * 60L), base,
                    base.add(new BigDecimal("0.18")),
                    base.subtract(new BigDecimal("0.12")),
                    base.add(new BigDecimal("0.05")),
                    new BigDecimal("100")));
        }
        for (int index = 5; index < 10; index++) {
            BigDecimal base = new BigDecimal("596.45").add(BigDecimal.valueOf((index - 5) * 0.25));
            candles.add(candle(start.plusSeconds(index * 60L), base,
                    base.add(new BigDecimal("0.35")),
                    base.subtract(new BigDecimal("0.08")),
                    base.add(new BigDecimal("0.28")),
                    new BigDecimal("260")));
        }

        when(candleRepository.findTop200BySymbolAndIntervalCodeAndClosedTrueOrderByOpenTimeDesc("ETHUSDT", "1m"))
                .thenReturn(candles.reversed());

        TrendStructureService service = new TrendStructureService(candleRepository);
        TrendStructureResult result = service.evaluate(indicator(
                start.plusSeconds(9 * 60L),
                new BigDecimal("597.73"),
                new BigDecimal("596.70"),
                new BigDecimal("596.35"),
                new BigDecimal("84"),
                new BigDecimal("0.25"),
                new BigDecimal("2.60")
        ));

        assertTrue(result.score() >= 4);
        assertTrue(result.higherHigh());
        assertTrue(result.higherLow());
        assertTrue(result.breakoutPreparationScore() == 1);
        assertTrue(result.continuationSupported());
        assertTrue(result.evidence().stream().anyMatch(e -> e.contains("broke prior structure")));
    }

    @Test
    void shouldNotUseHotRsiExceptionWithoutConfirmedExpansion() {
        Instant start = Instant.parse("2026-08-08T13:45:00Z");
        List<Candle> candles = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            BigDecimal base = new BigDecimal("596.00").add(BigDecimal.valueOf(index * 0.03));
            candles.add(candle(start.plusSeconds(index * 60L), base,
                    base.add(new BigDecimal("0.10")),
                    base.subtract(new BigDecimal("0.10")),
                    base.add(new BigDecimal("0.02")),
                    new BigDecimal("100")));
        }

        when(candleRepository.findTop200BySymbolAndIntervalCodeAndClosedTrueOrderByOpenTimeDesc("ETHUSDT", "1m"))
                .thenReturn(candles.reversed());

        TrendStructureService service = new TrendStructureService(candleRepository);
        TrendStructureResult result = service.evaluate(indicator(
                start.plusSeconds(9 * 60L),
                new BigDecimal("596.29"),
                new BigDecimal("596.10"),
                new BigDecimal("596.00"),
                new BigDecimal("84"),
                new BigDecimal("0.05"),
                new BigDecimal("1.10")
        ));

        assertTrue(!result.continuationSupported());
    }

    private IndicatorSnapshot indicator(Instant candleTime) {
        return new IndicatorSnapshot(
                "ETHUSDT", "1m", candleTime,
                new BigDecimal("1861.20"),
                new BigDecimal("1859.80"),
                new BigDecimal("1859.50"),
                new BigDecimal("1860.10"),
                new BigDecimal("1865.00"),
                new BigDecimal("57"),
                new BigDecimal("0.80"),
                new BigDecimal("0.50"),
                new BigDecimal("0.30"),
                new BigDecimal("1859.80"),
                new BigDecimal("1864.00"),
                new BigDecimal("1855.60"),
                new BigDecimal("0.45"),
                new BigDecimal("2.00"),
                new BigDecimal("120"),
                new BigDecimal("110"),
                new BigDecimal("1.09")
        );
    }

    private IndicatorSnapshot indicator(
            Instant candleTime,
            BigDecimal latestPrice,
            BigDecimal ema20,
            BigDecimal ema50,
            BigDecimal rsi,
            BigDecimal macdHistogram,
            BigDecimal relativeVolume
    ) {
        return new IndicatorSnapshot(
                "ETHUSDT", "1m", candleTime,
                latestPrice,
                ema20,
                ema20,
                ema50,
                ema50.subtract(new BigDecimal("0.50")),
                rsi,
                new BigDecimal("0.80"),
                new BigDecimal("0.50"),
                macdHistogram,
                ema20,
                ema20.add(new BigDecimal("1.50")),
                ema20.subtract(new BigDecimal("1.50")),
                new BigDecimal("0.45"),
                new BigDecimal("0.40"),
                new BigDecimal("260"),
                new BigDecimal("100"),
                relativeVolume
        );
    }

    private Candle candle(Instant openTime, BigDecimal open, BigDecimal high,
                          BigDecimal low, BigDecimal close, BigDecimal volume) {
        return Candle.builder()
                .symbol("ETHUSDT")
                .intervalCode("1m")
                .openTime(openTime)
                .closeTime(openTime.plusSeconds(59))
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low)
                .closePrice(close)
                .volume(volume)
                .closed(true)
                .build();
    }
}
