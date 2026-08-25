package com.crypto.repository;

import com.crypto.domain.TechnicalIndicator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TechnicalIndicatorRepository
        extends JpaRepository<TechnicalIndicator, Long> {

    Optional<TechnicalIndicator>
    findBySymbolAndIntervalCodeAndCandleOpenTime(
            String symbol,
            String intervalCode,
            Instant candleOpenTime
    );

    Optional<TechnicalIndicator>
    findTopBySymbolAndIntervalCodeOrderByCandleOpenTimeDesc(
            String symbol,
            String intervalCode
    );

    Optional<TechnicalIndicator>
    findTopBySymbolAndIntervalCodeAndCandleOpenTimeLessThanOrderByCandleOpenTimeDesc(
            String symbol,
            String intervalCode,
            Instant candleOpenTime
    );


    // FIX-092: Dashboard chart overlays must use the indicator values persisted for the
    // same historical candles being rendered. This read-only range query never feeds
    // chart data back into analysis, Replay, or execution.
    List<TechnicalIndicator>
    findBySymbolAndIntervalCodeAndCandleOpenTimeBetweenOrderByCandleOpenTimeAsc(
            String symbol,
            String intervalCode,
            Instant from,
            Instant to
    );

    List<TechnicalIndicator>
    findTop100BySymbolAndIntervalCodeOrderByCandleOpenTimeDesc(
            String symbol,
            String intervalCode
    );
}