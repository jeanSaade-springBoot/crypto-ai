package com.crypto.service;

import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.config.TradingProperties;
import com.crypto.domain.Candle;
import com.crypto.domain.TechnicalIndicator;
import com.crypto.domain.TradeSignal;
import com.crypto.indicator.service.TechnicalIndicatorService;
import com.crypto.repository.CandleRepository;
import com.crypto.repository.TechnicalIndicatorRepository;
import com.crypto.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledAnalysisService {

    private static final int RECOVERY_BATCH_SIZE = 120;

    private final TradingProperties properties;
    private final CoinConfigurationService coinConfigurationService;
    private final CandleRepository candleRepository;
    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final AnalysisService analysisService;
    private final PaperTradingService paperTradingService;
    private final TradeSignalRepository tradeSignalRepository;

    /**
     * FIX-043 chronological recovery flow for every configured symbol and interval.
     *
     * Production incident (ACEUSDT + all enabled symbols, 22 Aug 2026): candle persistence
     * was healthy (~97-100% of 1m candles) while technical_indicator/trade_signal coverage
     * was only ~17-20%. The previous five-minute job called calculateAndPersist(..., null),
     * which calculated ONLY the latest candle. If 12:21-12:25 were missed and 12:26 existed,
     * recovery analyzed 12:26 and permanently skipped the five intervening candles. That made
     * a nominal 1m strategy behave like a ~5m/6m strategy and directly contributed to late BUYs.
     *
     * Recovery now starts from persisted CLOSED candles and fills every incomplete candle in
     * chronological order. Existing indicators are reused when only trade_signal is missing.
     * Historical recovered rows are for continuity/audit/context only: they MUST NOT execute
     * a wallet trade at an old candle price. Only the latest closed candle may enter the live
     * execution path, and only while that candle is still fresh for its timeframe.
     *
     * The normal path remains the asynchronous CandleClosedEvent listener. This scheduler is a
     * safety net, not a trading clock. No BUY/SELL score, FIX-014 wake-up, FIX-021 accumulated
     * evidence, FIX-041 late-entry rule, FIX-042 RANGE veto, or exit rule is changed here.
     */
    @Scheduled(fixedDelayString = "${trading.analysis-delay-ms:60000}")
    public void analyzeConfiguredSymbols() {
        if (!properties.scheduledAnalysisEnabled()) {
            return;
        }

        Instant now = Instant.now();
        for (String symbol : coinConfigurationService.enabledSymbols()) {
            for (String interval : properties.intervals()) {
                recoverMissingAnalysis(symbol, interval, now);
            }
        }
    }

    void recoverMissingAnalysis(String symbol, String interval, Instant now) {
        try {
            Instant from = now.minus(recoveryLookback(interval));
            List<Candle> missing = candleRepository.findClosedCandlesMissingAnalysisThrough(
                    symbol,
                    interval,
                    from,
                    now,
                    PageRequest.of(0, RECOVERY_BATCH_SIZE)
            );

            if (missing.isEmpty()) {
                return;
            }

            Candle latestClosed = candleRepository
                    .findFirstBySymbolAndIntervalCodeAndClosedTrueOrderByCloseTimeDesc(symbol, interval)
                    .orElse(null);

            int recovered = 0;
            for (Candle candle : missing) {
                if (recoverCandle(symbol, interval, candle, latestClosed, now)) {
                    recovered++;
                }
            }

            log.warn(
                    "FIX-043 chronological analysis recovery completed: symbol={}, interval={}, " +
                            "missing={}, recovered={}, oldest={}, newest={}",
                    symbol,
                    interval,
                    missing.size(),
                    recovered,
                    missing.get(0).getOpenTime(),
                    missing.get(missing.size() - 1).getOpenTime()
            );
        } catch (Exception exception) {
            log.error(
                    "Scheduled chronological analysis recovery failed: symbol={}, interval={}",
                    symbol,
                    interval,
                    exception
            );
        }
    }

    private boolean recoverCandle(
            String symbol,
            String interval,
            Candle candle,
            Candle latestClosed,
            Instant now
    ) {
        try {
            TechnicalIndicator indicator = technicalIndicatorRepository
                    .findBySymbolAndIntervalCodeAndCandleOpenTime(symbol, interval, candle.getOpenTime())
                    .orElseGet(() -> technicalIndicatorService
                            .calculateAndPersist(symbol, interval, candle.getOpenTime())
                            .orElse(null));

            if (indicator == null) {
                log.info(
                        "FIX-043 recovery skipped indicator: symbol={}, interval={}, candleOpenTime={}, " +
                                "reason=insufficient/as-of history",
                        symbol,
                        interval,
                        candle.getOpenTime()
                );
                return false;
            }

            var existingSignal = tradeSignalRepository
                    .findBySymbolAndIntervalAndCandleOpenTime(symbol, interval, candle.getOpenTime());
            boolean newlyRecoveredSignal = existingSignal.isEmpty();
            TradeSignal signal = existingSignal
                    .orElseGet(() -> analysisService.analyzeRecovered(indicator, candle.getCloseTime()));

            // FIX-043 safety: never buy/sell now using a recovered historical candle price.
            // Only the newest closed candle can be an execution candidate, and even that candle
            // must still be fresh. Older recovered signals restore the exact chronological
            // evidence/context that Replay already has, but remain non-executing in live recovery.
            boolean latest = latestClosed != null
                    && latestClosed.getOpenTime().equals(candle.getOpenTime());
            boolean fresh = isFreshEnoughForExecution(candle, interval, now);
            if (latest && fresh && newlyRecoveredSignal) {
                paperTradingService.processSignal(signal);
                log.info(
                        "FIX-043 recovery executed fresh latest signal: symbol={}, interval={}, candleOpenTime={}, signalId={}",
                        symbol,
                        interval,
                        candle.getOpenTime(),
                        signal.getId()
                );
            } else {
                log.info(
                        "FIX-043 recovery backfilled without live execution: symbol={}, interval={}, candleOpenTime={}, " +
                                "signalId={}, latest={}, fresh={}, newlyRecoveredSignal={}",
                        symbol,
                        interval,
                        candle.getOpenTime(),
                        signal.getId(),
                        latest,
                        fresh,
                        newlyRecoveredSignal
                );
            }
            return true;
        } catch (Exception exception) {
            // Continue with the next candle. One corrupt/malformed row must not recreate a multi-minute blind gap.
            log.error(
                    "FIX-043 recovery failed for one candle: symbol={}, interval={}, candleOpenTime={}",
                    symbol,
                    interval,
                    candle.getOpenTime(),
                    exception
            );
            return false;
        }
    }

    private Duration recoveryLookback(String interval) {
        return switch (interval == null ? "" : interval) {
            case "1m" -> Duration.ofHours(6);
            case "5m" -> Duration.ofHours(24);
            case "1h" -> Duration.ofDays(7);
            case "4h" -> Duration.ofDays(14);
            case "1d" -> Duration.ofDays(60);
            default -> Duration.ofDays(1);
        };
    }

    private boolean isFreshEnoughForExecution(Candle candle, String interval, Instant now) {
        if (candle == null || candle.getCloseTime() == null || now == null) {
            return false;
        }
        Duration grace = switch (interval == null ? "" : interval) {
            case "1m" -> Duration.ofSeconds(90);
            case "5m" -> Duration.ofMinutes(2);
            case "1h" -> Duration.ofMinutes(5);
            case "4h" -> Duration.ofMinutes(10);
            case "1d" -> Duration.ofMinutes(30);
            default -> Duration.ofMinutes(2);
        };
        return !candle.getCloseTime().plus(grace).isBefore(now);
    }
}
