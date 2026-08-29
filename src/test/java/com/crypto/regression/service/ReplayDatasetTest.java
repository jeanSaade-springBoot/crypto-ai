package com.crypto.regression.service;

import com.crypto.domain.Candle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayDatasetTest {

    @Test
    void returnsExactAsOfDescendingLimitedSliceWithoutExposingBackingList() {
        List<Candle> candles = new ArrayList<>();
        for (int minute = 0; minute < 6; minute++) candles.add(candle("EDUUSDT", "1m", minute));
        Map<ReplayDataset.CandleKey, List<Candle>> byKey = new HashMap<>();
        byKey.put(new ReplayDataset.CandleKey("EDUUSDT", "1m"), candles);
        ReplayDataset dataset = new ReplayDataset(byKey, Map.of());

        List<Candle> result = dataset.closedCandlesAtOrBefore(
                "EDUUSDT", "1m", Instant.parse("2026-08-29T01:03:00Z"), 3);

        assertThat(result).extracting(Candle::getOpenTime).containsExactly(
                Instant.parse("2026-08-29T01:03:00Z"),
                Instant.parse("2026-08-29T01:02:00Z"),
                Instant.parse("2026-08-29T01:01:00Z"));
        result.clear();
        assertThat(dataset.closedCandlesAtOrBefore(
                "EDUUSDT", "1m", Instant.parse("2026-08-29T01:03:00Z"), 3)).hasSize(3);
    }

    @Test
    void exactFix112dLineageHasNoNearestFallback() {
        Instant at = Instant.parse("2026-08-29T01:24:00Z");
        ReplayDataset.SignalKey key = new ReplayDataset.SignalKey("EDUUSDT", "1m", at);
        ReplayDataset dataset = new ReplayDataset(Map.of(), Map.of(key, 320341L));

        assertThat(dataset.sourceSignalId("EDUUSDT", "1m", at)).isEqualTo(320341L);
        assertThat(dataset.sourceSignalId("EDUUSDT", "5m", at)).isNull();
        assertThat(dataset.sourceSignalId("EDUUSDT", "1m", at.plusSeconds(1))).isNull();
    }

    @Test
    void constructorDefensivelyCopiesInputs() {
        List<Candle> source = new ArrayList<>(List.of(candle("EDUUSDT", "1m", 0)));
        Map<ReplayDataset.CandleKey, List<Candle>> byKey = new HashMap<>();
        byKey.put(new ReplayDataset.CandleKey("EDUUSDT", "1m"), source);
        ReplayDataset dataset = new ReplayDataset(byKey, Map.of());
        source.add(candle("EDUUSDT", "1m", 1));
        byKey.clear();

        assertThat(dataset.closedCandlesAtOrBefore(
                "EDUUSDT", "1m", Instant.parse("2026-08-29T01:10:00Z"), 10)).hasSize(1);
    }

    private Candle candle(String symbol, String interval, int minute) {
        Candle candle = new Candle();
        candle.setSymbol(symbol);
        candle.setIntervalCode(interval);
        candle.setOpenTime(Instant.parse("2026-08-29T01:00:00Z").plusSeconds(minute * 60L));
        candle.setClosed(true);
        return candle;
    }
}
