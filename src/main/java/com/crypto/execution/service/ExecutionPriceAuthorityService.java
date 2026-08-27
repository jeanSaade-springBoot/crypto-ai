package com.crypto.execution.service;

import com.crypto.market.service.MarketPriceEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Objects;

/**
 * FIX-056 fresh execution-price authority.
 *
 * <p>TradeSignal.latestPrice is immutable decision-time evidence and must never be
 * treated as an execution fill. Production resolves the newest canonical Binance 1m
 * live-price event recorded by FIX-052. Administration Replay resolves the newest
 * replay price event already consumed by ExecutionReplayScope. This gives both paths
 * one price authority while preserving the original signal snapshot for audit.</p>
 */
@Service
@RequiredArgsConstructor
public class ExecutionPriceAuthorityService {
    private static final Duration MAX_EXECUTION_PRICE_AGE = Duration.ofSeconds(15);

    private final MarketPriceEventService marketPriceEventService;
    private final ExecutionReplayScope replayScope;

    public Optional<ExecutionPrice> resolve(String symbol, Instant referenceTime) {
        // FIX-109 / Replay parity: wall-clock fallback is forbidden. Every caller must
        // supply the evaluation clock explicitly so historical Replay can never silently
        // compare an old market event with the machine clock used by Production today.
        Instant reference = Objects.requireNonNull(referenceTime,
                "Execution price reference time is required");

        if (replayScope.active()) {
            return replayScope.latestPrice()
                    .filter(p -> symbol != null && symbol.equalsIgnoreCase(p.symbol()))
                    .filter(p -> !p.observedAt().isAfter(reference))
                    .filter(p -> !Duration.between(p.observedAt(), reference).isNegative())
                    .filter(p -> Duration.between(p.observedAt(), reference).compareTo(MAX_EXECUTION_PRICE_AGE) <= 0)
                    .map(p -> new ExecutionPrice(p.price(), p.observedAt(), "REPLAY_MARKET_PRICE_EVENT"));
        }

        return marketPriceEventService.findLatestAtOrBefore(symbol, reference)
                .filter(p -> Duration.between(p.observedAt(), reference).compareTo(MAX_EXECUTION_PRICE_AGE) <= 0)
                .map(p -> new ExecutionPrice(p.price(), p.observedAt(), "BINANCE_KLINE_LIVE_CLOSE"));
    }

    public record ExecutionPrice(BigDecimal price, Instant observedAt, String source) {}
}
