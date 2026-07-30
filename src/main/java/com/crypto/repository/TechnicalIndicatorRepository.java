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

    List<TechnicalIndicator>
    findTop100BySymbolAndIntervalCodeOrderByCandleOpenTimeDesc(
            String symbol,
            String intervalCode
    );
}