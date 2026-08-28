package com.crypto.debug.monitor.service;

import com.crypto.debug.monitor.domain.PriceMoveEvent;
import com.crypto.debug.monitor.domain.PriceMoveMonitorSettings;
import com.crypto.debug.monitor.dto.PriceMoveMonitorSettingsRequest;
import com.crypto.debug.monitor.dto.CatchingMarketPageResponse;
import com.crypto.debug.monitor.repository.PriceMoveEventRepository;
import com.crypto.debug.monitor.repository.PriceMoveMonitorSettingsRepository;
import com.crypto.domain.BtcContextStatus;
import com.crypto.domain.ConfluenceStatus;
import com.crypto.domain.LiquidityContextStatus;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.execution.domain.ExecutionOpportunity;
import com.crypto.execution.repository.ExecutionOpportunityRepository;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.repository.CandleRepository;
import com.crypto.wallet.domain.WalletTrade;
import com.crypto.wallet.repository.WalletTradeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PRICE-ONLY market catcher.
 *
 * IMPORTANT: detection never calls analysis, signal or execution services. It only observes price.
 * After an 8-hour UTC block is finished, a separate retrospective blame step compares the already
 * proven market move with immutable signals/trades that existed during that block.
 */
@Service
@RequiredArgsConstructor
public class PriceMoveMonitorService {
    private static final long SETTINGS_CACHE_MILLIS = 10_000L;
    private static final int BLOCK_HOURS = 8;
    private static final int HISTORY_DAYS = 3650; // caught moves are evidence; keep them long-term.
    private static final ObjectMapper BLAME_JSON = new ObjectMapper();

    private static final List<WindowRule> RULES = List.of(
            new WindowRule("30m", 30, bd("1.0"), bd("2.0"), bd("3.0")),
            new WindowRule("1h", 60, bd("1.5"), bd("2.5"), bd("4.0")),
            new WindowRule("2h", 120, bd("2.0"), bd("3.5"), bd("5.0")),
            new WindowRule("4h", 240, bd("3.0"), bd("5.0"), bd("7.0"))
    );

    private final PriceMoveMonitorSettingsRepository settingsRepository;
    private final PriceMoveEventRepository eventRepository;
    private final TradeSignalRepository signalRepository;
    private final CandleRepository candleRepository;
    private final WalletTradeRepository walletTradeRepository;
    // FIX-098: read-only retrospective diagnosis only. This repository is never written from PriceMoveMonitorService.
    private final ExecutionOpportunityRepository executionOpportunityRepository;

    private final Map<String, BlockTracker> trackers = new ConcurrentHashMap<>();
    private volatile PriceMoveMonitorSettings cachedSettings;
    private volatile long cachedSettingsAt;

    @Transactional
    public void onPrice(String rawSymbol, BigDecimal price, Instant observedAt) {
        if (rawSymbol == null || price == null || price.signum() <= 0 || observedAt == null) return;
        PriceMoveMonitorSettings settings = settings();
        if (!settings.isEnabled()) return;
        String symbol = rawSymbol.trim().toUpperCase(Locale.ROOT);
        if (!selectedSymbols(settings).contains(symbol)) return;

        Instant blockStart = blockStart(observedAt);
        BlockTracker tracker = trackers.computeIfAbsent(symbol, ignored -> new BlockTracker(blockStart));
        synchronized (tracker) {
            if (!tracker.blockStart.equals(blockStart)) {
                finalizeBlock(symbol, tracker);
                tracker.reset(blockStart);
            }
            tracker.observe(price, observedAt);
            evaluateWindows(tracker);
            // Persist/update the same row as the move grows. Blame stays PENDING until the block closes.
            syncLiveCatch(symbol, tracker, "UP", tracker.up);
            syncLiveCatch(symbol, tracker, "DOWN", tracker.down);
        }
    }

    private void evaluateWindows(BlockTracker tracker) {
        if (tracker.points.isEmpty()) return;
        PricePoint now = tracker.points.peekLast();
        for (WindowRule rule : RULES) {
            Instant cutoff = now.time.minus(rule.minutes, ChronoUnit.MINUTES);
            PricePoint low = null, high = null;
            for (PricePoint point : tracker.points) {
                if (point.time.isBefore(cutoff)) continue;
                if (low == null || point.price.compareTo(low.price) < 0) low = point;
                if (high == null || point.price.compareTo(high.price) > 0) high = point;
            }
            if (low != null && low.time.isBefore(now.time)) {
                BigDecimal up = pct(low.price, now.price);
                tracker.up.consider(low, now, up, rule);
            }
            if (high != null && high.time.isBefore(now.time)) {
                BigDecimal down = pct(high.price, now.price); // negative
                tracker.down.consider(high, now, down, rule);
            }
        }
    }

    /** One row per direction per 8h block; NORMAL -> HIGH -> EXTREME upgrades never create duplicates. */
    private void finalizeBlock(String symbol, BlockTracker tracker) {
        finalizeCatch(symbol, tracker, "UP", tracker.up);
        finalizeCatch(symbol, tracker, "DOWN", tracker.down);
    }

    private void syncLiveCatch(String symbol, BlockTracker tracker, String direction, Candidate candidate) {
        if (!candidate.caught()) return;
        PriceMoveEvent event = eventRepository.findBySymbolAndBlockStartTimeAndDirection(symbol, tracker.blockStart, direction).orElseGet(() -> PriceMoveEvent.builder()
                .symbol(symbol).direction(direction)
                .blockStartTime(tracker.blockStart).blockEndTime(tracker.blockStart.plus(BLOCK_HOURS, ChronoUnit.HOURS))
                .startTime(candidate.start.time).endTime(candidate.end.time)
                .startPrice(candidate.start.price).endPrice(candidate.end.price)
                .changePercent(candidate.change.setScale(8, RoundingMode.HALF_UP))
                .durationSeconds(Math.max(0, Duration.between(candidate.start.time, candidate.end.time).getSeconds()))
                .detectionWindow(candidate.window).importanceLevel(candidate.level)
                .outcomeStatus("PENDING").blameRequired(false).blameReviewed(false).reviewStatus("NEW")
                .build());
        event.setStartTime(candidate.start.time); event.setEndTime(candidate.end.time); event.setStartPrice(candidate.start.price); event.setEndPrice(candidate.end.price);
        event.setChangePercent(candidate.change.setScale(8,RoundingMode.HALF_UP)); event.setDurationSeconds(Math.max(0,Duration.between(candidate.start.time,candidate.end.time).getSeconds()));
        event.setDetectionWindow(candidate.window); event.setImportanceLevel(candidate.level);
        eventRepository.save(event);
    }

    private void finalizeCatch(String symbol, BlockTracker tracker, String direction, Candidate candidate) {
        if (!candidate.caught()) return;
        syncLiveCatch(symbol, tracker, direction, candidate);
        PriceMoveEvent event=eventRepository.findBySymbolAndBlockStartTimeAndDirection(symbol,tracker.blockStart,direction).orElseThrow();
        applyBlame(event); eventRepository.save(event);
    }

    /** Retrospective only: never feeds results back into trading. */
    private void applyBlame(PriceMoveEvent event) {
        boolean up = "UP".equals(event.getDirection());
        String wantedSide = up ? "BUY" : "SELL";
        List<WalletTrade> trades = walletTradeRepository.findTop100BySymbolAndStatusOrderByExecutedAtDesc(event.getSymbol(), "EXECUTED")
                .stream().filter(t -> wantedSide.equalsIgnoreCase(t.getSide()))
                .filter(t -> !t.getExecutedAt().isBefore(event.getStartTime()) && !t.getExecutedAt().isAfter(event.getEndTime()))
                .toList();
        if (!trades.isEmpty()) {
            event.setTradeId(trades.get(trades.size() - 1).getId());
            event.setOutcomeStatus("CAPTURED");
            event.setBlameRequired(false);
            event.setBlameExplanation("A matching " + wantedSide + " trade executed during the caught move.");
            return;
        }

        // FIX-097: caught moves are defined by market/candle time, so blame selection must prefer
        // candleOpenTime. generatedAt can lag the analyzed candle and caused valid signals to miss the
        // move window, leaving bestSignalId null. A generatedAt query is retained only as a legacy
        // fallback for older rows whose candleOpenTime was not persisted.
        TradeSignal best = resolveBestSignalForMove(event);
        if (best == null) {
            event.setOutcomeStatus("NOT_TRADABLE_EARLY");
            event.setBlameRequired(false);
            event.setBlameCode("NO_EARLY_SIGNAL_EVIDENCE");
            event.setBlameExplanation("The price move was caught, but no analysis snapshot existed inside the move window. Do not blame the strategy without evidence available at the time.");
            return;
        }
        event.setBestSignalId(best.getId());
        event.setBestSignalDecision(best.getDecision().name());
        event.setBestSignalScore(best.getTotalScore());

        boolean matchingSignal = isDirectionalMatch(best, up);
        if (matchingSignal) {
            event.setOutcomeStatus("SIGNALLED_NOT_TRADED");
            event.setBlameRequired(true);

            // FIX-098: once a directional signal existed, report the real retrospective execution
            // decision when an ExecutionOpportunity can be linked. Prefer the exact latest-signal
            // relationship; progressive opportunity accumulation can later advance latestSignal, so
            // lifecycle overlap is a safe read-only fallback. This never invokes execution logic.
            ExecutionOpportunity matchedOpportunity = resolveExecutionOpportunity(best, wantedSide);
            if (matchedOpportunity != null) {
                event.setBlameCode(hasText(matchedOpportunity.getDecisionCode())
                        ? matchedOpportunity.getDecisionCode() : "EXECUTION_NOT_COMPLETED");
                event.setBlameExplanation("A matching " + best.getDecision()
                        + " signal existed. Execution Intelligence: "
                        + safe(matchedOpportunity.getDecisionExplanation()));
            } else {
                event.setBlameCode("EXECUTION_NOT_COMPLETED");
                event.setBlameExplanation("A matching " + best.getDecision()
                        + " signal existed, but no matching wallet execution occurred. Final decision: "
                        + safe(best.getFinalDecisionExplanation()));
            }
            return;
        }

        event.setOutcomeStatus("MISSED_SIGNAL");
        event.setBlameRequired(true);
        event.setBlameCode(primaryBlocker(best, up));
        String explanation = "Best historical signal was " + best.getDecision() + " (score "
                + best.getTotalScore() + "). " + blockerExplanation(best, up);

        // FIX-098: soft context is useful only when there is no canonical or legacy hard blocker.
        // Keep the wording explicitly non-causal: these factors contributed context but individually
        // did not veto the signal.
        if (!hasHardBlocker(best)) {
            List<String> softWarnings = softWarnings(best);
            if (!softWarnings.isEmpty()) {
                explanation += " Contributing soft factors (none individually blocking): "
                        + String.join(", ", softWarnings) + ".";
            }
        }
        event.setBlameExplanation(explanation);
    }


    /**
     * FIX-097: resolve the best historical signal for the exact caught-move market-time window.
     * Candle time is authoritative because PriceMoveEvent.startTime/endTime describe the observed
     * market move. generatedAt is only a compatibility fallback for older signal records.
     */
    private TradeSignal resolveBestSignalForMove(PriceMoveEvent event) {
        if (event == null || event.getSymbol() == null || event.getStartTime() == null || event.getEndTime() == null) {
            return null;
        }

        Map<Long, TradeSignal> byId = new LinkedHashMap<>();
        signalRepository.findBySymbolAndCandleOpenTimeBetweenOrderByCandleOpenTimeAsc(
                        event.getSymbol(), event.getStartTime(), event.getEndTime())
                .forEach(signal -> byId.put(signal.getId(), signal));

        // Legacy compatibility: some older records may have null candleOpenTime even though generatedAt
        // falls inside the move. Merge them without duplicating signals already found by candle time.
        signalRepository.findBySymbolAndGeneratedAtBetweenOrderByGeneratedAtAsc(
                        event.getSymbol(), event.getStartTime(), event.getEndTime())
                .forEach(signal -> byId.putIfAbsent(signal.getId(), signal));

        List<TradeSignal> signals = new ArrayList<>(byId.values());
        if (signals.isEmpty()) return null;

        boolean up = "UP".equals(event.getDirection());
        return selectBestSignal(signals, up);
    }

    /**
     * FIX-098: one blamed signal per caught row, but choose the most diagnostically relevant one.
     * A real directional BUY/SELL is more important than a higher-scoring WATCH/NEUTRAL snapshot.
     * Within the same pool prefer signals that were not already explicitly deferred by ATR, then
     * preserve the historical score ordering. If every candidate was deferred, keep them eligible.
     */
    private TradeSignal selectBestSignal(List<TradeSignal> signals, boolean up) {
        List<TradeSignal> directional = signals.stream()
                .filter(signal -> isDirectionalMatch(signal, up))
                .toList();
        List<TradeSignal> pool = directional.isEmpty() ? signals : directional;

        List<TradeSignal> nonDeferred = pool.stream()
                .filter(signal -> !isAtrDeferred(signal))
                .toList();
        if (!nonDeferred.isEmpty()) pool = nonDeferred;

        return up
                ? pool.stream().max(Comparator.comparingInt(TradeSignal::getTotalScore)).orElse(null)
                : pool.stream().min(Comparator.comparingInt(TradeSignal::getTotalScore)).orElse(null);
    }

    private boolean isDirectionalMatch(TradeSignal signal, boolean up) {
        if (signal == null || signal.getDecision() == null) return false;
        return up
                ? signal.getDecision() == SignalDecision.BUY || signal.getDecision() == SignalDecision.STRONG_BUY
                : signal.getDecision() == SignalDecision.SELL || signal.getDecision() == SignalDecision.STRONG_SELL;
    }

    private boolean isAtrDeferred(TradeSignal signal) {
        if (signal == null || !hasText(signal.getAtrEntryType())) return false;
        return "PULLBACK_ENTRY".equalsIgnoreCase(signal.getAtrEntryType())
                || "WAIT_FOR_RETRACEMENT".equalsIgnoreCase(signal.getAtrEntryType());
    }

    /** FIX-098: read-only linkage from the blamed signal to the execution-opportunity audit trail. */
    private ExecutionOpportunity resolveExecutionOpportunity(TradeSignal signal, String wantedSide) {
        if (signal == null || !hasText(wantedSide)) return null;

        Optional<ExecutionOpportunity> exact = executionOpportunityRepository
                .findTopByLatestSignalIdOrderByUpdatedAtDesc(signal.getId());
        if (exact.isPresent() && wantedSide.equalsIgnoreCase(exact.get().getDirection())) {
            return exact.get();
        }

        Instant point = signal.getGeneratedAt() != null ? signal.getGeneratedAt() : signal.getCandleOpenTime();
        if (point == null) return null;
        return executionOpportunityRepository
                .findTop10BySymbolAndStartedAtLessThanEqualAndUpdatedAtGreaterThanEqualOrderByUpdatedAtDesc(
                        signal.getSymbol(), point, point)
                .stream()
                .filter(opportunity -> wantedSide.equalsIgnoreCase(opportunity.getDirection()))
                .findFirst()
                .orElse(null);
    }

    private String primaryBlocker(TradeSignal s, boolean up) {
        // FIX-098: FIX-091's canonical blocker is authoritative for newly persisted signals.
        if (hasText(s.getPrimaryBlockingStage())) return s.getPrimaryBlockingStage();

        // Legacy fallback: older signals pre-date primaryBlockingStage and must remain diagnosable.
        if (!s.isFinalEntryAllowed()) return "FINAL_ENTRY_BLOCKED";
        if (!s.isStrategyEntryAllowed()) return "STRATEGY_ENTRY_BLOCKED";
        if (!s.isConfluenceEntryAllowed()) return "MULTI_TIMEFRAME_BLOCKED";
        if (!s.isBtcContextEntryAllowed()) return "BTC_CONTEXT_BLOCKED";
        if (!s.isDerivativesEntryAllowed()) return "DERIVATIVES_BLOCKED";
        if (!s.isLiquidityEntryAllowed()) return "LIQUIDITY_BLOCKED";
        return up ? "BUY_THRESHOLD_NOT_REACHED" : "SELL_THRESHOLD_NOT_REACHED";
    }

    private String blockerExplanation(TradeSignal s, boolean up) {
        // FIX-098: the entity intentionally has no duplicate primaryBlockingExplanation field.
        // Pull the reason from the immutable decision_path stage selected by FinalDecisionService.
        if (hasText(s.getPrimaryBlockingStage())) {
            String canonicalReason = decisionPathReason(s, s.getPrimaryBlockingStage());
            if (hasText(canonicalReason)) return canonicalReason;
            return "Canonical blocker " + s.getPrimaryBlockingStage() + ": "
                    + safe(s.getFinalDecisionExplanation());
        }

        if (!s.isFinalEntryAllowed()) return "Final entry was blocked: " + safe(s.getFinalDecisionExplanation());
        if (!s.isStrategyEntryAllowed()) return "Strategy blocked entry: " + safe(s.getStrategyExplanation());
        if (!s.isConfluenceEntryAllowed()) return "Multi-timeframe confluence blocked entry: " + safe(s.getConfluenceExplanation());
        if (!s.isBtcContextEntryAllowed()) return "BTC context blocked entry: " + safe(s.getBtcContextExplanation());
        if (!s.isDerivativesEntryAllowed()) return "Derivatives positioning blocked entry: " + safe(s.getDerivativesExplanation());
        if (!s.isLiquidityEntryAllowed()) return "Liquidity/order-book context blocked entry: " + safe(s.getLiquidityExplanation());
        return "The directional score did not reach the required "
                + (up ? "BUY" : "SELL") + " decision threshold.";
    }

    private String decisionPathReason(TradeSignal signal, String stage) {
        if (signal == null || !hasText(stage) || !hasText(signal.getDecisionPath())) return null;
        try {
            JsonNode path = BLAME_JSON.readTree(signal.getDecisionPath());
            if (!path.isArray()) return null;
            for (JsonNode adjustment : path) {
                if (stage.equalsIgnoreCase(adjustment.path("source").asText())) {
                    String reason = adjustment.path("reason").asText(null);
                    if (hasText(reason)) return reason;
                }
            }
        } catch (Exception ignored) {
            // Retrospective diagnostics must never fail the monitor because one legacy JSON value is malformed.
        }
        return null;
    }

    private boolean hasHardBlocker(TradeSignal s) {
        return hasText(s.getPrimaryBlockingStage())
                || !s.isFinalEntryAllowed()
                || !s.isStrategyEntryAllowed()
                || !s.isConfluenceEntryAllowed()
                || !s.isBtcContextEntryAllowed()
                || !s.isDerivativesEntryAllowed()
                || !s.isLiquidityEntryAllowed();
    }

    private List<String> softWarnings(TradeSignal s) {
        List<String> warnings = new ArrayList<>();
        if (s.getBtcContextStatus() == BtcContextStatus.CONFLICT) {
            warnings.add("moderate BTC conflict");
        }
        if (s.getConfluenceStatus() == ConfluenceStatus.MIXED) {
            warnings.add("mixed higher-timeframe confluence");
        }
        if (s.getLiquidityStatus() == LiquidityContextStatus.LEARNING
                || s.getLiquidityStatus() == LiquidityContextStatus.INSUFFICIENT_DATA_HOLD) {
            warnings.add("thin order-book sampling");
        }
        return warnings;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Transactional(readOnly = true)
    public List<PriceMoveEvent> recentEvents(String rawSymbol) {
        return rawSymbol == null || rawSymbol.isBlank() ? eventRepository.findTop250ByOrderByEndTimeDesc()
                : eventRepository.findTop250BySymbolOrderByEndTimeDesc(rawSymbol.trim().toUpperCase(Locale.ROOT));
    }
    public long outstandingBlameCount() { return eventRepository.countByBlameRequiredTrueAndBlameReviewedFalse(); }

    /**
     * FIX-113: server-side Catching Market aggregation/pagination. The lookback changes only which
     * persisted price_move_event rows are summarized. Detection itself and all Production/Replay
     * trading behavior remain untouched. Page size is intentionally fixed at 20 for a predictable UI.
     */
    @Transactional(readOnly = true)
    public CatchingMarketPageResponse summaryPage(String rawSymbols, String rawLevel, int lookbackHours, int requestedPage) {
        int hours = List.of(1, 4, 24).contains(lookbackHours) ? lookbackHours : 24;
        int page = Math.max(0, requestedPage);
        String symbols = normalizeCsvSymbols(rawSymbols);
        String level = rawLevel == null ? "HIGH" : rawLevel.trim().toUpperCase(Locale.ROOT);
        if (!List.of("HIGH", "EXTREME", "NORMAL", "ALL").contains(level)) level = "HIGH";

        Page<com.crypto.debug.monitor.dto.CatchingMarketSummaryView> result = eventRepository.findSummaryPage(
                Instant.now().minus(Duration.ofHours(hours)), symbols, level, PageRequest.of(page, 20));
        return new CatchingMarketPageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private String normalizeCsvSymbols(String rawSymbols) {
        if (rawSymbols == null || rawSymbols.isBlank()) return null;
        List<String> normalized = Arrays.stream(rawSymbols.split(","))
                .map(String::trim).filter(v -> !v.isBlank()).map(v -> v.toUpperCase(Locale.ROOT))
                .distinct().toList();
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }

    /**
     * FIX-113: lightweight View Chart payload. Unlike the older blame chart this does not resolve
     * TradeSignal, ExecutionOpportunity or wallet evidence and does not load multi-day context. It
     * fetches only a bounded candle window around the persisted catch START time and highlights that
     * start point. This is presentation-only and cannot influence Production or Replay.
     */
    @Transactional(readOnly = true)
    public Map<String,Object> eventStartChart(Long id, String rawInterval) {
        PriceMoveEvent event = eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Market move event was not found"));
        String interval = normalizeChartInterval(rawInterval, event.getDetectionWindow());
        Instant anchor = event.getStartTime();
        Duration radius = switch (interval) {
            case "4h" -> Duration.ofDays(3);
            case "1h" -> Duration.ofHours(18);
            case "5m" -> Duration.ofHours(3);
            default -> Duration.ofHours(2);
        };
        List<com.crypto.domain.Candle> candles = candleRepository
                .findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeAsc(
                        event.getSymbol(), interval, anchor.minus(radius), anchor.plus(radius))
                .stream().filter(com.crypto.domain.Candle::isClosed).toList();

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("event", event);
        out.put("interval", interval);
        out.put("startTime", anchor);
        out.put("startPrice", event.getStartPrice());
        out.put("candles", candles);
        return out;
    }

    private String normalizeChartInterval(String rawInterval, String detectionWindow) {
        String requested = rawInterval == null ? "" : rawInterval.trim().toLowerCase(Locale.ROOT);
        if (List.of("5m", "1h", "4h").contains(requested)) return requested;
        if ("4h".equalsIgnoreCase(detectionWindow)) return "4h";
        if ("1h".equalsIgnoreCase(detectionWindow) || "2h".equalsIgnoreCase(detectionWindow)) return "1h";
        return "5m";
    }

    @Transactional(readOnly = true)
    public Map<String,Object> eventChart(Long id, String rawInterval) {
        PriceMoveEvent e=eventRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Market move event was not found"));

        // FIX-095: the blame chart is intentionally focused on exactly the signal selected by the
        // retrospective blame analysis. Do not mix unrelated signals or wallet executions into this
        // diagnostic popup; the operator asked to inspect the one persisted best/blamed signal only.
        TradeSignal blamedSignal = e.getBestSignalId() == null ? null
                : signalRepository.findById(e.getBestSignalId()).orElse(null);
        String blamedSignalResolution = blamedSignal == null ? "MOVE_WINDOW_RECONSTRUCTED" : "PERSISTED_ID";

        // FIX-097: historical/live caught-move rows may legitimately have no bestSignalId yet (for
        // example a PENDING block) or may have been created when generatedAt-based blame selection
        // missed a valid candle-time signal. Reconstruct the same deterministic best signal from
        // immutable TradeSignal rows instead of failing the read-only chart with HTTP 400. This does
        // not mutate the event and does not create/synthesize a signal.
        if (blamedSignal == null) {
            blamedSignal = resolveBestSignalForMove(e);
        }
        if (blamedSignal == null) {
            Map<String,Object> noSignal = new LinkedHashMap<>();
            noSignal.put("event", e);
            noSignal.put("blamedSignal", null);
            noSignal.put("blamedSignalResolution", "NONE");
            noSignal.put("blamedSignalMessage", "No persisted trade signal exists inside this caught move window, so there is no blamed signal to highlight.");
            noSignal.put("candles", List.of());
            return noSignal;
        }

        // FIX-095: always render the blamed signal on its native interval so its highlight cannot
        // disappear because the popup was switched to a different timeframe. rawInterval is kept in
        // the controller signature for backward compatibility with existing URLs, but the persisted
        // signal interval is authoritative for this focused diagnostic chart.
        String interval = blamedSignal.getInterval();
        if (interval == null || interval.isBlank()) interval = "1m";

        Instant signalTime = blamedSignal.getCandleOpenTime() != null
                ? blamedSignal.getCandleOpenTime() : blamedSignal.getGeneratedAt();
        if (signalTime == null) {
            throw new IllegalArgumentException("Blamed signal has no candle/generated timestamp to anchor the chart");
        }

        /*
         * FIX-096: the focused blame chart must be anchored to the blamed signal, not clipped to the
         * caught move's 8-hour monitoring block. A retrospective bestSignalId can legitimately point
         * to a signal near/outside that block boundary. FIX-095 clipped the candle query to blockFrom /
         * blockTo, which could produce an inverted/empty range and render a completely black popup.
         *
         * Build a deterministic read-only window around the persisted signal timestamp instead. The
         * window grows with timeframe so 1h/4h signals still receive enough candles for context.
         * This changes dashboard diagnostics only; no signal, Replay, execution or wallet state is used.
         */
        Duration nativeRadius = blameChartRadius(interval);
        Instant from = signalTime.minus(nativeRadius);
        Instant to = signalTime.plus(nativeRadius);
        List<com.crypto.domain.Candle> candles = candleRepository
                .findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeAsc(e.getSymbol(), interval, from, to)
                .stream().filter(com.crypto.domain.Candle::isClosed).toList();

        String chartInterval = interval;
        boolean fallbackIntervalUsed = false;

        /*
         * FIX-096: older DB periods may not contain candles for every signal timeframe. The blamed
         * signal still has a valid immutable candleOpenTime/price, so fall back to 1m candle context
         * when the native interval has no stored candles. We keep the signal interval separately and
         * still highlight only that one blamed signal. We never synthesize candles.
         */
        if (candles.isEmpty() && !"1m".equals(interval)) {
            chartInterval = "1m";
            fallbackIntervalUsed = true;
            Duration fallbackRadius = blameChartRadius(chartInterval);
            Instant fallbackFrom = signalTime.minus(fallbackRadius);
            Instant fallbackTo = signalTime.plus(fallbackRadius);
            candles = candleRepository
                    .findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeAsc(e.getSymbol(), chartInterval, fallbackFrom, fallbackTo)
                    .stream().filter(com.crypto.domain.Candle::isClosed).toList();
        }

        Map<String,Object> out=new LinkedHashMap<>();
        out.put("event",e);
        out.put("interval", chartInterval);
        out.put("signalInterval", interval);
        out.put("fallbackIntervalUsed", fallbackIntervalUsed);
        out.put("signalTime", signalTime);
        out.put("blamedSignal", blamedSignal);
        out.put("blamedSignalResolution", blamedSignalResolution);
        out.put("candles", candles);
        return out;
    }

    /**
     * FIX-096: timeframe-aware radius for the read-only blamed-signal popup. The radius is deliberately
     * based on the signal timeframe rather than the caught move block so the selected signal stays in
     * the middle of a useful candle context window.
     */
    private Duration blameChartRadius(String interval) {
        return switch (interval == null ? "1m" : interval) {
            case "4h" -> Duration.ofDays(10);
            case "1h" -> Duration.ofDays(3);
            case "15m" -> Duration.ofHours(18);
            case "5m" -> Duration.ofHours(8);
            default -> Duration.ofHours(2);
        };
    }

    @Transactional
    public PriceMoveEvent updateReviewStatus(Long id, String rawStatus) {
        String status = rawStatus == null ? "" : rawStatus.trim().toUpperCase(Locale.ROOT);
        if (!List.of("NEW", "REVIEWED", "IGNORED").contains(status)) throw new IllegalArgumentException("Review status must be NEW, REVIEWED or IGNORED");
        PriceMoveEvent event = eventRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Market move event was not found"));
        event.setReviewStatus(status);
        if (event.isBlameRequired() && !"NEW".equals(status)) event.setBlameReviewed(true);
        return eventRepository.save(event);
    }

    public Map<String,Object> activeTracker(String rawSymbol) {
        String symbol = rawSymbol == null ? "" : rawSymbol.trim().toUpperCase(Locale.ROOT);
        BlockTracker t = trackers.get(symbol);
        if (t == null) return Map.of("symbol", symbol, "phase", "WAITING_FOR_PRICE", "tracking", false);
        synchronized (t) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("symbol",symbol); m.put("phase","8H_BLOCK"); m.put("tracking",true); m.put("blockStart",t.blockStart); m.put("blockEnd",t.blockStart.plus(8,ChronoUnit.HOURS));
            m.put("lastPrice",t.points.isEmpty()?null:t.points.peekLast().price); m.put("up",t.up.asMap()); m.put("down",t.down.asMap()); return m;
        }
    }

    @Transactional(readOnly = true) public PriceMoveMonitorSettings currentSettings() { return settingsRepository.findById(1L).orElseGet(this::defaults); }
    @Transactional public PriceMoveMonitorSettings updateSettings(PriceMoveMonitorSettingsRequest request) {
        PriceMoveMonitorSettings s=settingsRepository.findById(1L).orElseGet(this::defaults); s.setId(1L); s.setEnabled(request.enabled()); s.setSelectedSymbols(normalizeSelectedSymbols(request.symbols()));
        // Legacy columns stay populated for schema compatibility but are no longer user-configurable or used by detection.
        if(s.getMinimumMovePercent()==null)s.setMinimumMovePercent(bd("1")); if(s.getWindowMinutes()<=0)s.setWindowMinutes(30); if(s.getMinimumDurationMinutes()<=0)s.setMinimumDurationMinutes(30);
        if(s.getRetracementClosePercent()==null)s.setRetracementClosePercent(bd("30")); if(s.getRetentionDays()<=0)s.setRetentionDays(HISTORY_DAYS);
        PriceMoveMonitorSettings saved=settingsRepository.save(s); cachedSettings=saved; cachedSettingsAt=System.currentTimeMillis(); trackers.clear(); return saved;
    }
    @Transactional public int cleanupExpired(){ return eventRepository.deleteOlderThan(Instant.now().minus(Duration.ofDays(HISTORY_DAYS))); }

    private PriceMoveMonitorSettings settings(){ long n=System.currentTimeMillis(); if(cachedSettings==null||n-cachedSettingsAt>SETTINGS_CACHE_MILLIS){cachedSettings=settingsRepository.findById(1L).orElseGet(this::defaults);cachedSettingsAt=n;} return cachedSettings; }
    private Set<String> selectedSymbols(PriceMoveMonitorSettings s){ if(s==null||s.getSelectedSymbols()==null||s.getSelectedSymbols().isBlank())return Set.of(); return Set.copyOf(Arrays.asList(s.getSelectedSymbols().split(","))); }
    private String normalizeSelectedSymbols(List<String> xs){ if(xs==null)return ""; return xs.stream().filter(Objects::nonNull).map(x->x.trim().toUpperCase(Locale.ROOT)).filter(x->x.matches("[A-Z0-9_-]{2,30}")).distinct().limit(100).collect(java.util.stream.Collectors.joining(",")); }
    private PriceMoveMonitorSettings defaults(){ return PriceMoveMonitorSettings.builder().id(1L).enabled(true).minimumMovePercent(bd("1")).windowMinutes(30).minimumDurationMinutes(30).retracementClosePercent(bd("30")).cooldownMinutes(0).retentionDays(HISTORY_DAYS).selectedSymbols("BNBUSDT").build(); }
    private static Instant blockStart(Instant t){ long epoch=t.getEpochSecond(); long size=BLOCK_HOURS*3600L; return Instant.ofEpochSecond((epoch/size)*size); }
    private static BigDecimal pct(BigDecimal a,BigDecimal b){ if(a==null||b==null||a.signum()==0)return BigDecimal.ZERO; return b.subtract(a).divide(a,12,RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)); }
    private static BigDecimal bd(String v){return new BigDecimal(v);} private static String safe(String s){return s==null?"No detailed explanation persisted.":s;}

    private record WindowRule(String name,int minutes,BigDecimal normal,BigDecimal high,BigDecimal extreme){ String level(BigDecimal abs){return abs.compareTo(extreme)>=0?"EXTREME":abs.compareTo(high)>=0?"HIGH":abs.compareTo(normal)>=0?"NORMAL":null;} }
    private record PricePoint(Instant time,BigDecimal price){}
    private static final class Candidate { PricePoint start,end; BigDecimal change=BigDecimal.ZERO; String level,window; boolean caught(){return level!=null;} void consider(PricePoint s,PricePoint e,BigDecimal c,WindowRule r){String l=r.level(c.abs());if(l==null)return;if(!caught()||rank(l)>rank(level)||c.abs().compareTo(change.abs())>0){start=s;end=e;change=c;level=l;window=r.name;}} Map<String,Object> asMap(){Map<String,Object>m=new LinkedHashMap<>();m.put("caught",caught());m.put("level",level);m.put("window",window);m.put("changePercent",change);m.put("startTime",start==null?null:start.time);m.put("startPrice",start==null?null:start.price);m.put("endTime",end==null?null:end.time);m.put("endPrice",end==null?null:end.price);return m;} static int rank(String x){return "EXTREME".equals(x)?3:"HIGH".equals(x)?2:"NORMAL".equals(x)?1:0;} }
    private static final class BlockTracker { Instant blockStart; final Deque<PricePoint> points=new ArrayDeque<>(); final Candidate up=new Candidate(),down=new Candidate(); BlockTracker(Instant s){blockStart=s;} void reset(Instant s){blockStart=s;points.clear();up.start=up.end=down.start=down.end=null;up.level=up.window=down.level=down.window=null;up.change=down.change=BigDecimal.ZERO;} void observe(BigDecimal p,Instant t){ if(!points.isEmpty()&&points.peekLast().time.truncatedTo(ChronoUnit.MINUTES).equals(t.truncatedTo(ChronoUnit.MINUTES))) points.removeLast(); points.addLast(new PricePoint(t,p)); Instant cutoff=t.minus(4,ChronoUnit.HOURS); while(!points.isEmpty()&&points.peekFirst().time.isBefore(cutoff))points.removeFirst(); } }
}
