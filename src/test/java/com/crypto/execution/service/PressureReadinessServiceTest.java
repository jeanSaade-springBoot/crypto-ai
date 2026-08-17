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
 * Fixtures are historical candle shapes; the test does not duplicate the detector.
 */
class PressureReadinessServiceTest {

    @Test
    void solSequenceBecomesReadyOnlyAfterRejectedBurstHigherLowAndPressureRebuild() {
        CandleRepository repository = mock(CandleRepository.class);
        PressureReadinessService service = new PressureReadinessService(repository);
        List<Candle> candles = solMorningSequence();
        candles.sort(Comparator.comparing(Candle::getOpenTime).reversed());

        when(repository.findClosedCandlesClosedAtOrBefore(eq("SOLUSDT"), eq("1m"), any(Instant.class), any(Pageable.class)))
                .thenReturn(candles);

        TradeSignal current = TradeSignal.builder()
                .symbol("SOLUSDT")
                .interval("1m")
                .candleOpenTime(Instant.parse("2026-08-17T00:58:00Z"))
                .generatedAt(Instant.parse("2026-08-17T01:00:01Z"))
                .build();

        PressureReadinessService.Result result = service.evaluate(current);

        assertThat(result.probeReady()).isTrue();
        assertThat(result.state()).isEqualTo(PressureReadinessService.State.PROBE_READY);
        assertThat(result.burstAt()).isEqualTo(Instant.parse("2026-08-17T00:43:00Z"));
        assertThat(result.retestLow()).isEqualByComparingTo("74.54");
        assertThat(result.structuralLow()).isLessThan(result.retestLow());
        assertThat(result.rebuildBuyCandles()).isGreaterThanOrEqualTo(3);
        assertThat(result.explanation()).contains("higher-low retest").contains("buyer pressure rebuilt");
    }

    @Test
    void firstSolBurstIsNotEnoughBeforeRetestAndRebuildComplete() {
        CandleRepository repository = mock(CandleRepository.class);
        PressureReadinessService service = new PressureReadinessService(repository);
        List<Candle> candles = solMorningSequence().stream()
                .filter(c -> !c.getOpenTime().isAfter(Instant.parse("2026-08-17T00:47:00Z")))
                .sorted(Comparator.comparing(Candle::getOpenTime).reversed())
                .toList();

        when(repository.findClosedCandlesClosedAtOrBefore(eq("SOLUSDT"), eq("1m"), any(Instant.class), any(Pageable.class)))
                .thenReturn(candles);

        TradeSignal current = TradeSignal.builder()
                .symbol("SOLUSDT")
                .interval("1m")
                .candleOpenTime(Instant.parse("2026-08-17T00:46:00Z"))
                .generatedAt(Instant.parse("2026-08-17T00:48:04Z"))
                .build();

        PressureReadinessService.Result result = service.evaluate(current);

        assertThat(result.probeReady()).isFalse();
        assertThat(result.state()).isEqualTo(PressureReadinessService.State.NORMAL);
    }

    @Test
    void asOfQueryUsesCandleCloseTimeNotOpenTime() {
        CandleRepository repository = mock(CandleRepository.class);
        PressureReadinessService service = new PressureReadinessService(repository);
        when(repository.findClosedCandlesClosedAtOrBefore(eq("SOLUSDT"), eq("1m"), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        Instant generatedAt = Instant.parse("2026-08-17T01:00:01Z");
        TradeSignal current = TradeSignal.builder().symbol("SOLUSDT").interval("1m")
                .generatedAt(generatedAt).build();
        service.evaluate(current);

        org.mockito.Mockito.verify(repository).findClosedCandlesClosedAtOrBefore(
                eq("SOLUSDT"), eq("1m"), eq(generatedAt), any(Pageable.class));
    }

    private List<Candle> solMorningSequence() {
        List<Candle> out = new ArrayList<>();
        // The real SOL sequence used in the investigation. Keeping the pre-burst portion
        // prevents the detector from inventing a convenient structural low after the fact.
        add(out,"00:20","74.54","74.60","74.54","74.59","1041.063","571.384");
        add(out,"00:21","74.60","74.60","74.56","74.57","363.239","52.064");
        add(out,"00:22","74.57","74.57","74.50","74.51","897.061","63.728");
        add(out,"00:23","74.50","74.53","74.49","74.51","1103.073","121.077");
        add(out,"00:24","74.51","74.51","74.45","74.45","1613.721","104.151");
        add(out,"00:25","74.45","74.49","74.43","74.49","1682.473","769.487");
        add(out,"00:26","74.48","74.49","74.48","74.49","254.420","60.709");
        add(out,"00:27","74.49","74.50","74.48","74.49","444.499","173.296");
        add(out,"00:28","74.48","74.57","74.48","74.57","1312.960","1069.951");
        add(out,"00:29","74.58","74.64","74.58","74.64","115.025","73.786");
        add(out,"00:30","74.64","74.66","74.60","74.62","969.295","191.211");
        add(out,"00:31","74.61","74.61","74.51","74.51","490.241","81.983");
        add(out,"00:32","74.52","74.54","74.51","74.52","250.890","92.197");
        add(out,"00:33","74.52","74.53","74.46","74.46","706.974","32.171");
        add(out,"00:34","74.46","74.52","74.46","74.50","1846.467","1211.595");
        add(out,"00:35","74.50","74.50","74.45","74.45","868.488","128.029");
        add(out,"00:36","74.46","74.47","74.44","74.46","362.573","205.303");
        add(out,"00:37","74.47","74.49","74.47","74.48","270.671","152.710");
        add(out,"00:38","74.49","74.57","74.48","74.57","814.005","551.192");
        add(out,"00:39","74.57","74.59","74.55","74.57","191.446","96.144");
        add(out,"00:40","74.57","74.57","74.53","74.54","131.457","36.051");
        add(out,"00:41","74.54","74.57","74.51","74.57","127.787","97.889");
        add(out,"00:42","74.57","74.57","74.55","74.55","326.314","56.009");
        add(out,"00:43","74.56","74.63","74.55","74.63","1531.824","1504.206");
        add(out,"00:44","74.63","74.75","74.63","74.74","2964.604","2221.451");
        add(out,"00:45","74.73","74.75","74.68","74.73","3802.393","2203.619");
        add(out,"00:46","74.73","74.74","74.62","74.63","3054.720","589.764");
        add(out,"00:47","74.64","74.64","74.60","74.61","319.618","115.579");
        add(out,"00:48","74.61","74.61","74.56","74.56","1180.925","570.726");
        add(out,"00:49","74.56","74.61","74.56","74.57","194.074","83.235");
        add(out,"00:50","74.56","74.59","74.55","74.58","208.541","42.556");
        add(out,"00:51","74.58","74.58","74.54","74.55","203.529","24.128");
        add(out,"00:52","74.55","74.56","74.54","74.55","217.490","67.180");
        add(out,"00:53","74.54","74.61","74.54","74.61","755.703","549.908");
        add(out,"00:54","74.61","74.62","74.59","74.61","192.939","121.116");
        add(out,"00:55","74.62","74.64","74.62","74.64","180.870","107.007");
        add(out,"00:56","74.63","74.66","74.63","74.66","96.417","90.328");
        add(out,"00:57","74.66","74.66","74.61","74.62","1028.500","86.486");
        add(out,"00:58","74.62","74.70","74.62","74.65","1070.645","817.743");
        add(out,"00:59","74.65","74.65","74.62","74.62","124.742","27.643");
        return out;
    }

    private void add(List<Candle> out, String hhmm, String open, String high, String low, String close,
                     String volume, String takerBuy) {
        Instant time = Instant.parse("2026-08-17T" + hhmm + ":00Z");
        out.add(Candle.builder().symbol("SOLUSDT").intervalCode("1m")
                .openTime(time).closeTime(time.plusSeconds(59))
                .openPrice(new BigDecimal(open)).highPrice(new BigDecimal(high))
                .lowPrice(new BigDecimal(low)).closePrice(new BigDecimal(close))
                .volume(new BigDecimal(volume)).takerBuyBaseVolume(new BigDecimal(takerBuy))
                .closed(true).build());
    }
}
