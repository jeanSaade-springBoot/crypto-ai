package com.crypto.controller;

import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.domain.Candle;
import com.crypto.domain.PaperPosition;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.PositionStatus;
import com.crypto.domain.TechnicalIndicator;
import com.crypto.domain.TradeSignal;
import com.crypto.execution.domain.ExecutionOpportunity;
import com.crypto.execution.repository.ExecutionOpportunityRepository;
import com.crypto.position.domain.PositionAnalysis;
import com.crypto.position.domain.PositionManagementEvent;
import com.crypto.position.repository.PositionAnalysisRepository;
import com.crypto.position.repository.PositionManagementEventRepository;
import com.crypto.service.ScheduleConfigurationService;
import com.crypto.service.ScoreDiagnosticsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.crypto.repository.CandleRepository;
import com.crypto.repository.PaperPositionRepository;
import com.crypto.repository.TechnicalIndicatorRepository;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.wallet.domain.WalletTrade;
import com.crypto.wallet.repository.WalletTradeRepository;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import com.crypto.wallet.domain.WalletManagedPosition;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardApiController {

    private final CandleRepository candleRepository;
    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final TradeSignalRepository tradeSignalRepository;
    private final PaperPositionRepository paperPositionRepository;
    private final ScheduleConfigurationService scheduleConfigurationService;
    private final ObjectMapper objectMapper;
    private final ScoreDiagnosticsService scoreDiagnosticsService;
    private final CoinConfigurationService coinConfigurationService;
    private final WalletTradeRepository walletTradeRepository;
    private final WalletManagedPositionRepository walletManagedPositionRepository;
    private final ExecutionOpportunityRepository executionOpportunityRepository;
    private final PositionAnalysisRepository positionAnalysisRepository;
    private final PositionManagementEventRepository positionManagementEventRepository;
    private final Map<String, AggregatedCandleCacheEntry> aggregatedCandleCache = new ConcurrentHashMap<>();

    public DashboardApiController(
            CandleRepository candleRepository,
            TechnicalIndicatorRepository technicalIndicatorRepository,
            TradeSignalRepository tradeSignalRepository,
            PaperPositionRepository paperPositionRepository,
            ScheduleConfigurationService scheduleConfigurationService,
            ObjectMapper objectMapper,
            ScoreDiagnosticsService scoreDiagnosticsService,
            CoinConfigurationService coinConfigurationService,
            WalletTradeRepository walletTradeRepository,
            WalletManagedPositionRepository walletManagedPositionRepository,
            ExecutionOpportunityRepository executionOpportunityRepository,
            PositionAnalysisRepository positionAnalysisRepository,
            PositionManagementEventRepository positionManagementEventRepository
    ) {
        this.candleRepository = candleRepository;
        this.technicalIndicatorRepository = technicalIndicatorRepository;
        this.tradeSignalRepository = tradeSignalRepository;
        this.paperPositionRepository = paperPositionRepository;
        this.scheduleConfigurationService = scheduleConfigurationService;
        this.objectMapper = objectMapper;
        this.scoreDiagnosticsService = scoreDiagnosticsService;
        this.coinConfigurationService = coinConfigurationService;
        this.walletTradeRepository = walletTradeRepository;
        this.walletManagedPositionRepository = walletManagedPositionRepository;
        this.executionOpportunityRepository = executionOpportunityRepository;
        this.positionAnalysisRepository = positionAnalysisRepository;
        this.positionManagementEventRepository = positionManagementEventRepository;
    }

    @GetMapping("/symbols")
    public List<String> symbols() {
        List<String> symbols = coinConfigurationService.enabledSymbols();
        return symbols.isEmpty() ? List.of("BTCUSDT") : symbols;
    }


    /**
     * Actionable signal evidence is loaded separately from the heavy dashboard overview.
     * The default TODAY window is based on Riyadh local day, while persisted timestamps remain UTC.
     * This avoids the old Top-20 problem where recent NEUTRAL/WATCH rows could hide older BUY/SELL
     * evidence and also keeps historical evidence on-demand.
     */
    @GetMapping("/signals")
    @Transactional(readOnly = true)
    public Map<String, Object> signals(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam(defaultValue = "TODAY") String period,
            @RequestParam(defaultValue = "ALL") String executionFilter,
            @RequestParam(defaultValue = "50") int limit
    ) {
        String normalizedSymbol = symbol.trim().toUpperCase();
        String normalizedInterval = interval.trim().toLowerCase();
        String normalizedPeriod = period == null ? "TODAY" : period.trim().toUpperCase();
        String normalizedExecutionFilter = executionFilter == null ? "ALL" : executionFilter.trim().toUpperCase();
        int safeLimit = Math.max(1, Math.min(limit, 250));
        List<SignalDecision> actionable = List.of(
                SignalDecision.BUY, SignalDecision.STRONG_BUY,
                SignalDecision.SELL, SignalDecision.STRONG_SELL
        );

        Instant from = signalEvidenceFrom(normalizedPeriod);
        List<TradeSignal> rows = from == null
                ? tradeSignalRepository.findBySymbolAndIntervalAndDecisionInOrderByGeneratedAtDesc(
                        normalizedSymbol, normalizedInterval, actionable, PageRequest.of(0, safeLimit))
                : tradeSignalRepository.findBySymbolAndIntervalAndDecisionInAndGeneratedAtGreaterThanEqualOrderByGeneratedAtDesc(
                        normalizedSymbol, normalizedInterval, actionable, from, PageRequest.of(0, safeLimit));

        // Resolve wallet execution once for all returned signal ids. BUY_BLOCKED is based
        // on the immutable final_entry_allowed flag and therefore remains visible even
        // when no wallet trade was ever created for the blocked signal.
        List<Long> signalIds = rows.stream().map(TradeSignal::getId).toList();
        Map<Long, WalletTrade> executionBySignalId = new LinkedHashMap<>();
        if (!signalIds.isEmpty()) {
            for (WalletTrade trade : walletTradeRepository.findBySignal_IdInAndStatus(signalIds, "EXECUTED")) {
                if (trade.getSignal() == null || trade.getSignal().getId() == null) continue;
                executionBySignalId.putIfAbsent(trade.getSignal().getId(), trade);
            }
        }

        List<Map<String, Object>> filteredSignals = rows.stream()
                .filter(signal -> signalEvidenceMatchesExecutionFilter(
                        signal, executionBySignalId.get(signal.getId()), normalizedExecutionFilter))
                .map(signal -> signalEvidenceDto(signal, executionBySignalId.get(signal.getId())))
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("symbol", normalizedSymbol);
        response.put("interval", normalizedInterval);
        response.put("period", normalizedPeriod);
        response.put("executionFilter", normalizedExecutionFilter);
        response.put("from", from);
        response.put("count", filteredSignals.size());
        response.put("signals", filteredSignals);
        return response;
    }

    private Instant signalEvidenceFrom(String period) {
        ZoneId riyadh = ZoneId.of("Asia/Riyadh");
        ZonedDateTime now = ZonedDateTime.now(riyadh);
        return switch (period) {
            case "TODAY" -> now.toLocalDate().atStartOfDay(riyadh).toInstant();
            case "15M" -> now.minusMinutes(15).toInstant();
            case "4H" -> now.minusHours(4).toInstant();
            case "2H" -> now.minusHours(2).toInstant();
            case "1H" -> now.minusHours(1).toInstant();
            case "1W" -> now.minusDays(7).toInstant();
            case "ALL" -> null;
            default -> now.toLocalDate().atStartOfDay(riyadh).toInstant();
        };
    }

    private boolean signalEvidenceMatchesExecutionFilter(
            TradeSignal signal,
            WalletTrade execution,
            String filter
    ) {
        String decision = signal.getDecision() == null ? "" : signal.getDecision().name();
        boolean buy = "BUY".equals(decision) || "STRONG_BUY".equals(decision);
        boolean sell = "SELL".equals(decision) || "STRONG_SELL".equals(decision);
        return switch (filter) {
            case "EXECUTED" -> execution != null;
            case "BUY_BLOCKED" -> buy && !signal.isFinalEntryAllowed();
            // FIX-092: Dashboard Signals filter can inspect SELL evidence without changing
            // signal generation or wallet behavior. BUY_SELL intentionally means all persisted
            // actionable BUY/STRONG_BUY/SELL/STRONG_SELL decisions in the selected window.
            case "SELL_SIGNAL" -> sell;
            case "BUY_SELL" -> buy || sell;
            default -> true;
        };
    }

    private Map<String, Object> signalEvidenceDto(TradeSignal signal, WalletTrade execution) {
        Map<String, Object> result = new LinkedHashMap<>(signalDto(signal));
        // FIX-092: View chart anchors to the immutable signal candle/price, never to the
        // current clock, so blocked BUY and BUY/SELL evidence opens at the exact signal.
        result.put("candleOpenTime", signal.getCandleOpenTime());
        result.put("executionState", execution == null ? "NOT_EXECUTED" : "EXECUTED");
        result.put("executionId", execution == null ? null : execution.getId());
        result.put("executedSide", execution == null ? null : execution.getSide());
        result.put("executionReason", execution == null ? null : execution.getExecutionReason());
        result.put("executedAt", execution == null ? null : execution.getExecutedAt());
        result.put("executedPrice", execution == null ? null : execution.getPriceUsdt());
        result.put("executedQuantity", execution == null ? null : execution.getQuantity());
        result.put("executedAmountUsdt", execution == null ? null : execution.getGrossAmountUsdt());
        boolean buy = signal.getDecision() == SignalDecision.BUY || signal.getDecision() == SignalDecision.STRONG_BUY;
        result.put("buyPositionBlocked", buy && !signal.isFinalEntryAllowed());
        return result;
    }

    @GetMapping("/score-diagnostics")
    @Transactional(readOnly = true)
    public Map<String, Object> scoreDiagnostics() {
        return scoreDiagnosticsService.last24Hours();
    }

    @GetMapping("/runtime-configuration")
    public Map<String, Object> runtimeConfiguration() {
        return scheduleConfigurationService.dashboardSchedule();
    }


    /**
     * Lightweight market payload used when the user changes symbol/timeframe.
     * It intentionally avoids signals history, positions, wallet executions,
     * pipeline, counts and timeframe snapshots so the candlestick chart can
     * update immediately while the full dashboard continues loading in the
     * background. This is presentation-only and does not affect trading logic.
     */
    @GetMapping("/chart")
    @Transactional(readOnly = true)
    public Map<String, Object> chart(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam(required = false) Instant focusStart,
            @RequestParam(required = false) Instant focusEnd
    ) {
        String normalizedSymbol = symbol.trim().toUpperCase();
        String normalizedInterval = interval.trim().toLowerCase();
        boolean displayOnlyInterval = isDisplayOnlyInterval(normalizedInterval);

        List<Candle> candles = displayOnlyInterval
                ? loadAggregatedCandles(normalizedSymbol, normalizedInterval, 120)
                : loadDashboardCandles(normalizedSymbol, normalizedInterval, focusStart, focusEnd);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("symbol", normalizedSymbol);
        response.put("interval", normalizedInterval);
        response.put("displayOnlyInterval", displayOnlyInterval);
        response.put("updatedAt", Instant.now());
        response.put("candles", candles.stream().map(this::candleDto).toList());
        // FIX-092: Bollinger/ATR are chart-only overlays read from the same persisted
        // technical_indicator rows as the visible candle window. Display-only 4h/1d
        // aggregation has no native persisted indicator series, so it remains empty.
        response.put("indicatorSeries", displayOnlyInterval ? List.of() : indicatorSeries(normalizedSymbol, normalizedInterval, candles));
        response.put("livePrice", currentLatestPrice(candles));
        response.put("activePosition", walletManagedPositionRepository
                .findTopBySymbolAndStatusOrderByOpenedAtDesc(normalizedSymbol, "OPEN")
                .map(this::activePositionChartDto)
                .orElse(null));
        return response;
    }

    /**
     * Older candles for Binance-style click/drag dashboard navigation.
     * This endpoint is read-only and is intentionally separate from analysis,
     * signal generation and execution. The browser asks for another page only
     * when the user pans past the oldest candle already loaded.
     */
    @GetMapping("/chart-history")
    @Transactional(readOnly = true)
    public Map<String, Object> chartHistory(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam Instant before,
            @RequestParam(defaultValue = "180") int limit
    ) {
        String normalizedSymbol = symbol.trim().toUpperCase();
        String normalizedInterval = interval.trim().toLowerCase();
        int safeLimit = Math.max(30, Math.min(limit, 500));

        List<Candle> candles = isDisplayOnlyInterval(normalizedInterval)
                ? loadAggregatedCandlesAtOrBefore(normalizedSymbol, normalizedInterval, before, safeLimit)
                : loadClosedCandlesAtOrBefore(normalizedSymbol, normalizedInterval, before, safeLimit);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("symbol", normalizedSymbol);
        response.put("interval", normalizedInterval);
        response.put("before", before);
        response.put("count", candles.size());
        response.put("candles", candles.stream().map(this::candleDto).toList());
        response.put("indicatorSeries", isDisplayOnlyInterval(normalizedInterval) ? List.of() : indicatorSeries(normalizedSymbol, normalizedInterval, candles));
        return response;
    }

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public Map<String, Object> overview(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam(required = false) Instant focusStart,
            @RequestParam(required = false) Instant focusEnd
    ) {
        String normalizedSymbol = symbol.trim().toUpperCase();
        String normalizedInterval = interval.trim().toLowerCase();
        boolean displayOnlyInterval = isDisplayOnlyInterval(normalizedInterval);

        List<Candle> candles = displayOnlyInterval
                ? loadAggregatedCandles(normalizedSymbol, normalizedInterval, 120)
                : loadDashboardCandles(normalizedSymbol, normalizedInterval, focusStart, focusEnd);

        TechnicalIndicator latestIndicator = displayOnlyInterval ? null : technicalIndicatorRepository
                .findTopBySymbolAndIntervalCodeOrderByCandleOpenTimeDesc(normalizedSymbol, normalizedInterval)
                .orElse(null);

        TradeSignal latestSignal = displayOnlyInterval ? null : tradeSignalRepository
                .findTopBySymbolAndIntervalOrderByGeneratedAtDesc(normalizedSymbol, normalizedInterval)
                .orElse(null);

        List<TradeSignal> signals = displayOnlyInterval ? List.of() : tradeSignalRepository
                .findTop20BySymbolAndIntervalOrderByGeneratedAtDesc(normalizedSymbol, normalizedInterval);

        List<PaperPosition> positions = paperPositionRepository
                .findTop20BySymbolOrderByOpenedAtDesc(normalizedSymbol);

        long closedCandleCount = displayOnlyInterval
                ? candles.size()
                : candleRepository.countBySymbolAndIntervalCodeAndClosedTrue(normalizedSymbol, normalizedInterval);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("symbol", normalizedSymbol);
        response.put("interval", normalizedInterval);
        response.put("displayOnlyInterval", displayOnlyInterval);
        response.put("displayOnlyMessage", displayOnlyInterval
                ? normalizedInterval.toUpperCase() + " candles are derived from closed 1h candles and never influence trading decisions."
                : null);
        response.put("updatedAt", Instant.now());
        response.put("summary", summary(candles, latestIndicator, latestSignal, positions, closedCandleCount, displayOnlyInterval));
        response.put("pipeline", displayOnlyInterval
                ? displayOnlyPipeline(closedCandleCount)
                : pipeline(normalizedSymbol, closedCandleCount, latestIndicator, latestSignal, positions));
        response.put("candles", candles.stream().map(this::candleDto).toList());
        response.put("indicatorSeries", displayOnlyInterval ? List.of() : indicatorSeries(normalizedSymbol, normalizedInterval, candles));
        response.put("indicator", indicatorDto(latestIndicator));
        response.put("schedule", scheduleConfigurationService.dashboardSchedule());
        response.put("signals", signals.stream().map(this::signalDto).toList());
        response.put("timeframeSnapshot", timeframeSnapshot(normalizedSymbol));
        response.put("livePrice", candleRepository
                .findFirstBySymbolAndIntervalCodeAndClosedTrueOrderByCloseTimeDesc(normalizedSymbol, "1m")
                .map(Candle::getClosePrice)
                .orElse(currentLatestPrice(candles)));
        response.put("executions", walletTradeRepository
                .findTop100BySymbolAndStatusOrderByExecutedAtDesc(normalizedSymbol, "EXECUTED")
                .stream()
                .map(this::walletExecutionDto)
                .toList());

        BigDecimal currentPrice = candles.isEmpty()
                ? null
                : candles.get(candles.size() - 1).getClosePrice();

        List<Map<String, Object>> positionDtos = positions.stream()
                .map(position -> positionDto(position, currentPrice, latestSignal))
                .toList();
        response.put("positions", positionDtos);
        response.put("openPositions", positionDtos.stream()
                .filter(position -> "OPEN".equals(position.get("status")))
                .toList());
        response.put("closedPositions", positionDtos.stream()
                .filter(position -> !"OPEN".equals(position.get("status")))
                .toList());
        response.put("activePosition", walletManagedPositionRepository
                .findTopBySymbolAndStatusOrderByOpenedAtDesc(normalizedSymbol, "OPEN")
                .map(this::activePositionChartDto)
                .orElse(null));
        return response;
    }


    /**
     * Lazy drill-down for Dashboard "View analysis".
     *
     * The signal row already carries the immutable analysis snapshot. This endpoint
     * adds the execution layers introduced later in the system (opportunity evidence,
     * health/momentum, execution decision, progressive position state and profit
     * protection) only when the user explicitly opens the analysis. Keeping this
     * separate avoids making normal dashboard/symbol/timeframe refreshes slower.
     */
    @GetMapping("/signal-analysis-details")
    @Transactional(readOnly = true)
    public Map<String, Object> signalAnalysisDetails(@RequestParam Long signalId) {
        TradeSignal signal = tradeSignalRepository.findById(signalId).orElse(null);
        if (signal == null) {
            return Map.of("signalId", signalId, "found", false);
        }

        ExecutionOpportunity opportunity = executionOpportunityRepository
                .findTopByLatestSignalIdOrderByUpdatedAtDesc(signalId)
                .orElse(null);
        WalletTrade execution = walletTradeRepository
                .findTopBySignalIdAndStatusOrderByExecutedAtDesc(signalId, "EXECUTED")
                .orElse(null);
        WalletManagedPosition position = walletManagedPositionRepository
                .findTopByEntrySignalIdOrderByOpenedAtDesc(signalId)
                .orElse(null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("signalId", signalId);
        response.put("found", true);
        response.put("opportunity", opportunity == null ? null : executionOpportunityDto(opportunity));
        response.put("execution", execution == null ? null : walletExecutionDto(execution));
        response.put("position", position == null ? null : managedPositionAnalysisDto(position));
        return response;
    }


    @GetMapping("/active-positions")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> activePositions() {
        return walletManagedPositionRepository.findAllByStatusOrderByOpenedAtDesc("OPEN")
                .stream()
                .map(position -> {
                    BigDecimal currentPrice = candleRepository
                            .findFirstBySymbolAndIntervalCodeAndClosedTrueOrderByCloseTimeDesc(position.getSymbol(), "1m")
                            .map(Candle::getClosePrice)
                            .orElse(position.getAverageEntryPriceUsdt());

                    BigDecimal quantity = position.getQuantity() == null ? BigDecimal.ZERO : position.getQuantity();
                    BigDecimal entry = position.getAverageEntryPriceUsdt() == null ? BigDecimal.ZERO : position.getAverageEntryPriceUsdt();
                    BigDecimal unrealizedPnl = currentPrice.subtract(entry).multiply(quantity);
                    BigDecimal unrealizedPercent = entry.signum() == 0
                            ? BigDecimal.ZERO
                            : currentPrice.subtract(entry)
                                    .divide(entry, 8, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100));

                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("id", position.getId());
                    dto.put("entrySignalId", position.getEntrySignalId());
                    dto.put("symbol", position.getSymbol());
                    dto.put("quantity", quantity);
                    dto.put("entryPrice", entry);
                    dto.put("currentPrice", currentPrice);
                    dto.put("unrealizedPnlUsdt", unrealizedPnl);
                    dto.put("unrealizedPnlPercent", unrealizedPercent);
                    dto.put("stopLoss", position.getStopLossUsdt());
                    dto.put("takeProfit", position.getTakeProfitUsdt());
                    dto.put("highestPrice", position.getHighestPriceUsdt());
                    dto.put("profitLockActive", position.isProfitLockActive());
                    dto.put("profitLockPrice", position.getProfitLockPriceUsdt());
                    dto.put("profitLockProgressPercent", position.getProfitLockProgressPercent());
                    dto.put("entryStage", position.getEntryStage());
                    dto.put("allocatedPositionPercent", position.getAllocatedPositionPercent());
                    dto.put("entryQualityScore", position.getEntryQualityScore());
                    dto.put("lastScaleInAt", position.getLastScaleInAt());
                    dto.put("openedAt", position.getOpenedAt());
                    return dto;
                })
                .toList();
    }



    /**
     * FIX-053: Dashboard BUY/SELL evidence is now centered on the currently open
     * wallet position. The selected window limits the visible management-analysis
     * path; it does not alter Production analysis or execution behavior.
     */
    @GetMapping("/active-position-analysis")
    @Transactional(readOnly = true)
    public Map<String, Object> activePositionAnalysis(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "1H") String window
    ) {
        String normalizedSymbol = symbol.trim().toUpperCase();
        String normalizedWindow = normalizePositionAnalysisWindow(window);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("symbol", normalizedSymbol);
        response.put("window", normalizedWindow);

        WalletManagedPosition position = walletManagedPositionRepository
                .findTopBySymbolAndStatusOrderByOpenedAtDesc(normalizedSymbol, "OPEN")
                .orElse(null);
        if (position == null) {
            response.put("active", false);
            response.put("position", null);
            response.put("analysisPath", List.of());
            response.put("managementEvents", List.of());
            return response;
        }

        Instant windowFrom = positionAnalysisWindowFrom(normalizedWindow);
        Instant from = windowFrom.isAfter(position.getOpenedAt()) ? windowFrom : position.getOpenedAt();
        List<PositionAnalysis> analyses = positionAnalysisRepository
                .findByWalletPositionIdAndAnalyzedAtGreaterThanEqualOrderByAnalyzedAtAsc(position.getId(), from);
        List<PositionManagementEvent> events = positionManagementEventRepository
                .findByWalletPositionIdAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(position.getId(), from);

        TradeSignal entrySignal = position.getEntrySignalId() == null ? null
                : tradeSignalRepository.findById(position.getEntrySignalId()).orElse(null);
        BigDecimal latestPrice = candleRepository
                .findFirstBySymbolAndIntervalCodeAndClosedTrueOrderByCloseTimeDesc(normalizedSymbol, "1m")
                .map(Candle::getClosePrice)
                .orElse(position.getAverageEntryPriceUsdt());

        Map<String, Object> positionDto = new LinkedHashMap<>();
        positionDto.put("id", position.getId());
        positionDto.put("symbol", position.getSymbol());
        positionDto.put("entrySignalId", position.getEntrySignalId());
        positionDto.put("openedAt", position.getOpenedAt());
        positionDto.put("entryPrice", position.getAverageEntryPriceUsdt());
        positionDto.put("currentPrice", latestPrice);
        positionDto.put("quantity", position.getQuantity());
        positionDto.put("stopLoss", position.getStopLossUsdt());
        positionDto.put("takeProfit", position.getTakeProfitUsdt());
        positionDto.put("initialTakeProfit", entrySignal == null ? null : entrySignal.getTakeProfit());
        positionDto.put("profitLockActive", position.isProfitLockActive());
        positionDto.put("profitLockPrice", position.getProfitLockPriceUsdt());
        positionDto.put("highestPrice", position.getHighestPriceUsdt());
        positionDto.put("entryStage", position.getEntryStage());
        positionDto.put("allocatedPositionPercent", position.getAllocatedPositionPercent());
        positionDto.put("entryDecision", position.getEntryDecision());
        positionDto.put("entryDecisionPath", parseDecisionPath(position.getEntryDecisionPathJson()));
        positionDto.put("entryAnalysisSnapshot", parseAnalysisBreakdown(position.getEntryAnalysisSnapshotJson()));

        List<Map<String, Object>> path = analyses.stream().map(a -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("analysisId", a.getId());
            item.put("analyzedAt", a.getAnalyzedAt());
            item.put("interval", a.getIntervalCode());
            item.put("recommendation", a.getRecommendation() == null ? null : a.getRecommendation().name());
            item.put("confidence", a.getConfidence());
            item.put("exitScore", a.getExitScore());
            item.put("currentPrice", a.getCurrentPriceUsdt());
            item.put("unrealizedPnlPercent", a.getUnrealizedPnlPercent());
            item.put("explanation", a.getExplanation());
            item.put("details", parseAnalysisBreakdown(a.getDetailsJson()));
            TradeSignal signal = a.getTradeSignal();
            item.put("signalId", signal == null ? null : signal.getId());
            item.put("decision", signal == null || signal.getDecision() == null ? null : signal.getDecision().name());
            item.put("score", signal == null ? null : signal.getTotalScore());
            item.put("decisionPath", signal == null ? List.of() : parseDecisionPath(signal.getDecisionPath()));
            return item;
        }).toList();

        List<Map<String, Object>> management = events.stream().map(e -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("type", e.getEventType());
            item.put("oldValue", e.getOldValueUsdt());
            item.put("newValue", e.getNewValueUsdt());
            item.put("marketPrice", e.getMarketPriceUsdt());
            item.put("reason", e.getReason());
            item.put("occurredAt", e.getOccurredAt());
            return item;
        }).toList();

        response.put("active", true);
        response.put("from", from);
        response.put("position", positionDto);
        response.put("analysisPath", path);
        response.put("managementEvents", management);
        return response;
    }

    private String normalizePositionAnalysisWindow(String window) {
        String normalized = window == null ? "1H" : window.trim().toUpperCase();
        return switch (normalized) {
            case "15M", "1H", "4H", "1D", "1W" -> normalized;
            default -> "1H";
        };
    }

    private Instant positionAnalysisWindowFrom(String window) {
        Instant now = Instant.now();
        return switch (window) {
            case "15M" -> now.minus(Duration.ofMinutes(15));
            case "4H" -> now.minus(Duration.ofHours(4));
            case "1D" -> now.minus(Duration.ofDays(1));
            case "1W" -> now.minus(Duration.ofDays(7));
            default -> now.minus(Duration.ofHours(1));
        };
    }

    private Map<String, Object> activePositionChartDto(WalletManagedPosition position) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", position.getId());
        result.put("symbol", position.getSymbol());
        result.put("openedAt", position.getOpenedAt());
        result.put("entryPrice", position.getAverageEntryPriceUsdt());
        result.put("stopLoss", position.getStopLossUsdt());
        result.put("takeProfit", position.getTakeProfitUsdt());
        result.put("profitLockActive", position.isProfitLockActive());
        result.put("profitLockPrice", position.getProfitLockPriceUsdt());
        result.put("highestPrice", position.getHighestPriceUsdt());
        return result;
    }

    private Map<String, Object> timeframeSnapshot(String symbol) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("1h", timeframeDecision(symbol, "1h", "FIRST FRAME", "Strategic direction"));
        result.put("5m", timeframeDecision(symbol, "5m", "SECOND FRAME", "Confirmation"));
        result.put("1m", timeframeDecision(symbol, "1m", "THIRD FRAME", "Execution trigger"));
        return result;
    }

    private Map<String, Object> timeframeDecision(String symbol, String interval, String frame, String role) {
        TradeSignal signal = tradeSignalRepository
                .findTopBySymbolAndIntervalOrderByGeneratedAtDesc(symbol, interval)
                .orElse(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("frame", frame);
        result.put("role", role);
        result.put("interval", interval);
        result.put("signalId", signal == null ? null : signal.getId());
        result.put("decision", signal == null || signal.getDecision() == null ? "NO_SIGNAL" : signal.getDecision().name());
        result.put("score", signal == null ? null : signal.getTotalScore());
        result.put("confidence", signal == null ? null : signal.getConfidenceScore());
        result.put("price", signal == null ? null : signal.getLatestPrice());
        result.put("generatedAt", signal == null ? null : signal.getGeneratedAt());
        return result;
    }

    private boolean isDisplayOnlyInterval(String interval) {
        return "4h".equals(interval) || "1d".equals(interval);
    }

    private List<Candle> loadClosedCandles(String symbol, String interval, int limit) {
        List<Candle> candles = candleRepository.findClosedCandles(symbol, interval, PageRequest.of(0, limit));
        Collections.reverse(candles);
        return candles;
    }

    /**
     * Debug chart navigation may request a historical move window. This is read-only
     * presentation logic and is intentionally isolated from analysis/execution.
     */
    private List<Candle> loadDashboardCandles(
            String symbol,
            String interval,
            Instant focusStart,
            Instant focusEnd
    ) {
        // FIX-092: Signal "View chart" deep-links may target 1m, 5m or 1h. Historical
        // focusing is presentation-only, so honor the requested persisted timeframe instead
        // of limiting focus navigation to the older 5m debug use case.
        if (focusStart == null || focusEnd == null || !focusEnd.isAfter(focusStart)) {
            return loadClosedCandles(symbol, interval, 120);
        }

        Instant from = focusStart.minus(Duration.ofMinutes(30));
        Instant to = focusEnd.plus(Duration.ofMinutes(30));
        return candleRepository
                .findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeAsc(symbol, interval, from, to)
                .stream()
                .filter(Candle::isClosed)
                .limit(500)
                .toList();
    }

    private List<Candle> loadClosedCandlesAtOrBefore(
            String symbol,
            String interval,
            Instant before,
            int limit
    ) {
        List<Candle> candles = candleRepository.findClosedCandlesAtOrBefore(
                symbol, interval, before, PageRequest.of(0, limit));
        Collections.reverse(candles);
        return candles;
    }

    private List<Candle> loadAggregatedCandlesAtOrBefore(
            String symbol,
            String interval,
            Instant before,
            int targetBuckets
    ) {
        int bucketHours = "4h".equals(interval) ? 4 : 24;
        int sourceLimit = targetBuckets * bucketHours + bucketHours;
        List<Candle> oneHourCandles = candleRepository.findClosedCandlesAtOrBefore(
                symbol, "1h", before, PageRequest.of(0, sourceLimit));
        Collections.reverse(oneHourCandles);
        if (oneHourCandles.isEmpty()) return List.of();

        long bucketMillis = Duration.ofHours(bucketHours).toMillis();
        Map<Long, List<Candle>> grouped = new LinkedHashMap<>();
        for (Candle candle : oneHourCandles) {
            long bucketStart = Math.floorDiv(candle.getOpenTime().toEpochMilli(), bucketMillis) * bucketMillis;
            grouped.computeIfAbsent(bucketStart, ignored -> new ArrayList<>()).add(candle);
        }

        List<Candle> result = new ArrayList<>();
        for (Map.Entry<Long, List<Candle>> entry : grouped.entrySet()) {
            List<Candle> bucket = entry.getValue();
            if (bucket.size() != bucketHours) continue;
            bucket.sort(java.util.Comparator.comparing(Candle::getOpenTime));
            Candle first = bucket.get(0);
            Candle last = bucket.get(bucket.size() - 1);
            BigDecimal high = bucket.stream().map(Candle::getHighPrice).max(BigDecimal::compareTo).orElse(first.getHighPrice());
            BigDecimal low = bucket.stream().map(Candle::getLowPrice).min(BigDecimal::compareTo).orElse(first.getLowPrice());
            BigDecimal volume = bucket.stream().map(Candle::getVolume).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal quoteVolume = bucket.stream().map(Candle::getQuoteAssetVolume).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            long trades = bucket.stream().map(Candle::getNumberOfTrades).filter(java.util.Objects::nonNull).mapToLong(Long::longValue).sum();
            BigDecimal takerBase = bucket.stream().map(Candle::getTakerBuyBaseVolume).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal takerQuote = bucket.stream().map(Candle::getTakerBuyQuoteVolume).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

            result.add(Candle.builder()
                    .symbol(symbol)
                    .intervalCode(interval)
                    .openTime(Instant.ofEpochMilli(entry.getKey()))
                    .closeTime(last.getCloseTime())
                    .openPrice(first.getOpenPrice())
                    .highPrice(high)
                    .lowPrice(low)
                    .closePrice(last.getClosePrice())
                    .volume(volume)
                    .quoteAssetVolume(quoteVolume)
                    .numberOfTrades(trades)
                    .takerBuyBaseVolume(takerBase)
                    .takerBuyQuoteVolume(takerQuote)
                    .closed(true)
                    .build());
        }
        return result.size() <= targetBuckets
                ? List.copyOf(result)
                : List.copyOf(result.subList(result.size() - targetBuckets, result.size()));
    }

    private List<Candle> loadAggregatedCandles(String symbol, String interval, int targetBuckets) {
        String cacheKey = symbol + ":" + interval + ":" + targetBuckets;
        AggregatedCandleCacheEntry cached = aggregatedCandleCache.get(cacheKey);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) return cached.candles();

        int bucketHours = "4h".equals(interval) ? 4 : 24;
        int sourceLimit = targetBuckets * bucketHours + bucketHours;
        List<Candle> oneHourCandles = loadClosedCandles(symbol, "1h", sourceLimit);
        if (oneHourCandles.isEmpty()) {
            aggregatedCandleCache.put(cacheKey, new AggregatedCandleCacheEntry(now.plusSeconds(20), List.of()));
            return List.of();
        }

        long bucketMillis = Duration.ofHours(bucketHours).toMillis();
        Map<Long, List<Candle>> grouped = new LinkedHashMap<>();
        for (Candle candle : oneHourCandles) {
            long bucketStart = Math.floorDiv(candle.getOpenTime().toEpochMilli(), bucketMillis) * bucketMillis;
            grouped.computeIfAbsent(bucketStart, ignored -> new ArrayList<>()).add(candle);
        }

        List<Candle> result = new ArrayList<>();
        for (Map.Entry<Long, List<Candle>> entry : grouped.entrySet()) {
            List<Candle> bucket = entry.getValue();
            if (bucket.size() != bucketHours) continue;
            bucket.sort(java.util.Comparator.comparing(Candle::getOpenTime));
            Candle first = bucket.get(0);
            Candle last = bucket.get(bucket.size() - 1);
            BigDecimal high = bucket.stream().map(Candle::getHighPrice).max(BigDecimal::compareTo).orElse(first.getHighPrice());
            BigDecimal low = bucket.stream().map(Candle::getLowPrice).min(BigDecimal::compareTo).orElse(first.getLowPrice());
            BigDecimal volume = bucket.stream().map(Candle::getVolume).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal quoteVolume = bucket.stream().map(Candle::getQuoteAssetVolume).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            long trades = bucket.stream().map(Candle::getNumberOfTrades).filter(java.util.Objects::nonNull).mapToLong(Long::longValue).sum();
            BigDecimal takerBase = bucket.stream().map(Candle::getTakerBuyBaseVolume).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal takerQuote = bucket.stream().map(Candle::getTakerBuyQuoteVolume).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

            result.add(Candle.builder()
                    .symbol(symbol)
                    .intervalCode(interval)
                    .openTime(Instant.ofEpochMilli(entry.getKey()))
                    .closeTime(last.getCloseTime())
                    .openPrice(first.getOpenPrice())
                    .highPrice(high)
                    .lowPrice(low)
                    .closePrice(last.getClosePrice())
                    .volume(volume)
                    .quoteAssetVolume(quoteVolume)
                    .numberOfTrades(trades)
                    .takerBuyBaseVolume(takerBase)
                    .takerBuyQuoteVolume(takerQuote)
                    .closed(true)
                    .build());
        }
        List<Candle> limited = result.size() <= targetBuckets
                ? List.copyOf(result)
                : List.copyOf(result.subList(result.size() - targetBuckets, result.size()));
        aggregatedCandleCache.put(cacheKey, new AggregatedCandleCacheEntry(now.plusSeconds(60), limited));
        return limited;
    }

    private record AggregatedCandleCacheEntry(Instant expiresAt, List<Candle> candles) {}

    private Map<String, Object> displayOnlyPipeline(long closedCandleCount) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candle", status(closedCandleCount > 0, closedCandleCount + " aggregated candles"));
        result.put("indicator", status(false, "Display-only timeframe; indicators remain on 1m / 5m / 1h"));
        result.put("marketContext", status(false, "Display-only timeframe"));
        result.put("strategy", status(false, "Trading strategy not evaluated on this timeframe"));
        result.put("derivatives", status(false, "Trading context remains on engine timeframes"));
        result.put("orderBook", status(false, "Trading context remains on engine timeframes"));
        result.put("analysis", status(false, "No BUY/SELL decision generated for display-only timeframe"));
        result.put("executionValidation", status(false, "Display-only timeframe; execution validation remains on 1m / 5m / 1h"));
        result.put("walletExecution", status(false, "Wallet execution remains driven by validated 1m signals"));
        result.put("positionManager", status(false, "Position Manager monitors executed wallet positions"));
        result.put("walletTrade", status(false, "No display-only timeframe wallet execution"));
        result.put("tradeInspector", status(false, "Trade Inspector analyzes completed wallet trades, not display-only candles"));
        return result;
    }

    private Map<String, Object> summary(
            List<Candle> candles,
            TechnicalIndicator indicator,
            TradeSignal signal,
            List<PaperPosition> positions,
            long closedCandleCount,
            boolean displayOnlyInterval
    ) {
        Candle latest = candles.isEmpty() ? null : candles.get(candles.size() - 1);
        Candle previous = candles.size() < 2 ? null : candles.get(candles.size() - 2);
        BigDecimal changePercent = BigDecimal.ZERO;
        if (latest != null && previous != null && previous.getClosePrice().signum() != 0) {
            changePercent = latest.getClosePrice()
                    .subtract(previous.getClosePrice())
                    .divide(previous.getClosePrice(), 8, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        long openPositions = positions.stream()
                .filter(position -> position.getStatus() == PositionStatus.OPEN)
                .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("latestPrice", latest == null ? null : latest.getClosePrice());
        result.put("priceChangePercent", changePercent);
        result.put("closedCandleCount", closedCandleCount);
        result.put("minimumCandles", displayOnlyInterval ? 0 : 210);
        result.put("indicatorReady", indicator != null);
        result.put("latestDecision", displayOnlyInterval ? "DISPLAY_ONLY" : (signal == null ? "NO_SIGNAL" : signal.getDecision().name()));
        result.put("latestScore", signal == null ? null : signal.getTotalScore());
        result.put("openPositions", openPositions);
        return result;
    }

    private Map<String, Object> pipeline(
            String symbol,
            long closedCandleCount,
            TechnicalIndicator indicator,
            TradeSignal signal,
            List<PaperPosition> positions
    ) {
        WalletTrade latestWalletTrade = walletTradeRepository
                .findTopBySymbolAndStatusOrderByExecutedAtDesc(symbol, "EXECUTED")
                .orElse(null);
        WalletManagedPosition managedPosition = walletManagedPositionRepository
                .findTopBySymbolAndStatusOrderByOpenedAtDesc(symbol, "OPEN")
                .orElse(null);

        boolean signalReady = signal != null;
        boolean actionableDecision = signalReady && (
                "BUY".equals(signal.getDecision().name())
                || "STRONG_BUY".equals(signal.getDecision().name())
                || "SELL".equals(signal.getDecision().name())
                || "STRONG_SELL".equals(signal.getDecision().name())
        );
        boolean executionEligible = actionableDecision
                && "1m".equalsIgnoreCase(signal.getInterval())
                && signal.isFinalEntryAllowed();
        boolean hasWalletExecution = latestWalletTrade != null;
        boolean hasCompletedTrade = latestWalletTrade != null
                && "SELL".equalsIgnoreCase(latestWalletTrade.getSide());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candle", status(closedCandleCount > 0,
                closedCandleCount + " closed candles"));
        result.put("indicator", status(indicator != null,
                indicator == null
                        ? "Waiting for 210 closed candles or next close event"
                        : "Saved at " + indicator.getCandleOpenTime()));
        result.put("marketContext", status(signalReady,
                signal == null ? "Waiting for analysis" : signal.getMarketRegime().name()));
        result.put("strategy", status(signalReady,
                signal == null ? "Waiting for market context" : signal.getSelectedStrategy().name()));
        result.put("derivatives", status(signalReady,
                signal == null ? "Waiting for signal" : signal.getDerivativesStatus().name()));
        result.put("orderBook", status(signalReady,
                signal == null ? "Collecting depth" : signal.getLiquidityStatus().name()));
        result.put("analysis", status(signalReady,
                signal == null ? "No persisted trade signal yet" : signal.getDecision().name()));
        result.put("executionValidation", status(executionEligible,
                signal == null
                        ? "Waiting for final decision"
                        : executionEligible
                            ? "1m execution signal passed final entry validation"
                            : "Only validated 1m execution signals can reach the wallet"));
        result.put("walletExecution", status(hasWalletExecution,
                latestWalletTrade == null
                        ? "No executed wallet trade for " + symbol + " yet"
                        : latestWalletTrade.getSide() + " executed at " + latestWalletTrade.getPriceUsdt()));
        result.put("positionManager", status(managedPosition != null || hasCompletedTrade,
                managedPosition != null
                        ? "OPEN · SL / TP / Dynamic Profit Lock monitoring"
                        : hasCompletedTrade
                            ? "Latest wallet position completed"
                            : "Waiting for an executed BUY position"));
        result.put("walletTrade", status(hasWalletExecution,
                latestWalletTrade == null
                        ? "Trade History starts after wallet execution"
                        : "Wallet trade #" + latestWalletTrade.getId() + " · " + latestWalletTrade.getExecutionReason()));
        result.put("tradeInspector", status(hasCompletedTrade,
                hasCompletedTrade
                        ? "Completed wallet trade available for inspection"
                        : "Trade Inspector activates after a wallet position closes"));
        return result;
    }

    private Map<String, Object> status(boolean complete, String detail) {
        return Map.of("complete", complete, "detail", detail);
    }

    private BigDecimal currentLatestPrice(List<Candle> candles) {
        return candles.isEmpty() ? null : candles.get(candles.size() - 1).getClosePrice();
    }

    private Map<String, Object> executionOpportunityDto(ExecutionOpportunity opportunity) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", opportunity.getId());
        result.put("symbol", opportunity.getSymbol());
        result.put("direction", opportunity.getDirection());
        result.put("status", opportunity.getStatus());
        result.put("startedAt", opportunity.getStartedAt());
        result.put("lastEvidenceAt", opportunity.getLastEvidenceAt());
        result.put("evidenceCount", opportunity.getEvidenceCount());
        result.put("buyCount", opportunity.getBuyCount());
        result.put("watchCount", opportunity.getWatchCount());
        result.put("neutralCount", opportunity.getNeutralCount());
        result.put("bearishCount", opportunity.getBearishCount());
        result.put("evidenceScore", opportunity.getEvidenceScore());
        result.put("opportunityHealth", opportunity.getOpportunityHealth());
        result.put("healthMomentum", opportunity.getHealthMomentum());
        result.put("evidenceMomentum", opportunity.getEvidenceMomentum());
        result.put("lastBearishAt", opportunity.getLastBearishAt());
        result.put("averageSignalScore", opportunity.getAverageSignalScore());
        result.put("averageConfidence", opportunity.getAverageConfidence());
        result.put("fiveMinuteDecision", opportunity.getFiveMinuteDecision());
        result.put("oneHourDecision", opportunity.getOneHourDecision());
        result.put("executionSource", opportunity.getExecutionSource());
        result.put("recommendedPositionPercent", opportunity.getRecommendedPositionPercent());
        result.put("decisionCode", opportunity.getDecisionCode());
        result.put("decisionExplanation", opportunity.getDecisionExplanation());
        result.put("executedAt", opportunity.getExecutedAt());
        result.put("updatedAt", opportunity.getUpdatedAt());
        return result;
    }

    private Map<String, Object> managedPositionAnalysisDto(WalletManagedPosition position) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", position.getId());
        result.put("symbol", position.getSymbol());
        result.put("status", position.getStatus());
        result.put("openedAt", position.getOpenedAt());
        result.put("updatedAt", position.getUpdatedAt());
        result.put("averageEntryPrice", position.getAverageEntryPriceUsdt());
        result.put("quantity", position.getQuantity());
        result.put("entryStage", position.getEntryStage());
        result.put("allocatedPositionPercent", position.getAllocatedPositionPercent());
        result.put("entryQualityScore", position.getEntryQualityScore());
        result.put("entryConfidence", position.getEntryConfidence());
        result.put("entryTotalScore", position.getEntryTotalScore());
        result.put("entryTrendScore", position.getEntryTrendScore());
        result.put("entryStructureScore", position.getEntryStructureScore());
        result.put("entryMomentumScore", position.getEntryMomentumScore());
        result.put("entryVolumeScore", position.getEntryVolumeScore());
        result.put("stopLoss", position.getStopLossUsdt());
        result.put("takeProfit", position.getTakeProfitUsdt());
        result.put("highestPrice", position.getHighestPriceUsdt());
        result.put("profitLockActive", position.isProfitLockActive());
        result.put("profitLockPrice", position.getProfitLockPriceUsdt());
        result.put("profitLockProgressPercent", position.getProfitLockProgressPercent());
        result.put("profitLockActivatedAt", position.getProfitLockActivatedAt());
        result.put("lastScaleInAt", position.getLastScaleInAt());
        return result;
    }

    private Map<String, Object> walletExecutionDto(WalletTrade trade) {
        Map<String, Object> result = new LinkedHashMap<>();
        TradeSignal signal = trade.getSignal();
        result.put("id", trade.getId());
        result.put("symbol", trade.getSymbol());
        result.put("side", trade.getSide());
        result.put("executedAt", trade.getExecutedAt());
        result.put("price", trade.getPriceUsdt());
        result.put("quantity", trade.getQuantity());
        result.put("amountUsdt", trade.getNetAmountUsdt());
        result.put("realizedPnlUsdt", trade.getRealizedPnlUsdt());
        result.put("realizedPnlPercent", trade.getRealizedPnlPercent());
        result.put("executionReason", trade.getExecutionReason());
        result.put("executionType", trade.getExecutionType());
        result.put("signalId", signal == null ? null : signal.getId());
        result.put("timeframe", signal == null ? null : signal.getInterval());
        result.put("decision", signal == null ? null : signal.getDecision().name());
        result.put("score", signal == null ? null : signal.getTotalScore());
        result.put("confidence", signal == null ? null : signal.getConfidenceScore());
        return result;
    }

    private Map<String, Object> candleDto(Candle candle) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("time", candle.getOpenTime().toEpochMilli());
        result.put("openTime", candle.getOpenTime().toEpochMilli());
        result.put("closeTime", candle.getCloseTime() == null ? null : candle.getCloseTime().toEpochMilli());
        result.put("open", candle.getOpenPrice());
        result.put("high", candle.getHighPrice());
        result.put("low", candle.getLowPrice());
        result.put("close", candle.getClosePrice());
        result.put("volume", candle.getVolume());
        return result;
    }

    /**
     * FIX-092: Load only indicators that belong to the visible candle interval.
     * This is presentation data for Bollinger/ATR overlays and is intentionally
     * read-only; no value is recomputed in the browser or reused by trading logic.
     */
    private List<Map<String, Object>> indicatorSeries(String symbol, String interval, List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return List.of();
        Instant from = candles.get(0).getOpenTime();
        Instant to = candles.get(candles.size() - 1).getOpenTime();
        return technicalIndicatorRepository
                .findBySymbolAndIntervalCodeAndCandleOpenTimeBetweenOrderByCandleOpenTimeAsc(symbol, interval, from, to)
                .stream()
                .map(indicator -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("time", indicator.getCandleOpenTime().toEpochMilli());
                    row.put("bollingerUpper", indicator.getBollingerUpper());
                    row.put("bollingerMiddle", indicator.getBollingerMiddle());
                    row.put("bollingerLower", indicator.getBollingerLower());
                    row.put("atr14", indicator.getAtr14());
                    return row;
                })
                .toList();
    }

    private Map<String, Object> indicatorDto(TechnicalIndicator indicator) {
        if (indicator == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candleOpenTime", indicator.getCandleOpenTime());
        result.put("closePrice", indicator.getClosePrice());
        result.put("sma20", indicator.getSma20());
        result.put("ema20", indicator.getEma20());
        result.put("ema50", indicator.getEma50());
        result.put("ema200", indicator.getEma200());
        result.put("rsi14", indicator.getRsi14());
        result.put("macd", indicator.getMacd());
        result.put("macdSignal", indicator.getMacdSignal());
        result.put("macdHistogram", indicator.getMacdHistogram());
        result.put("bollingerUpper", indicator.getBollingerUpper());
        result.put("bollingerMiddle", indicator.getBollingerMiddle());
        result.put("bollingerLower", indicator.getBollingerLower());
        result.put("atr14", indicator.getAtr14());
        result.put("volumeSma20", indicator.getVolumeSma20());
        result.put("relativeVolume", indicator.getRelativeVolume());
        return result;
    }

    private Map<String, Object> signalDto(TradeSignal signal) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", signal.getId());
        result.put("symbol", signal.getSymbol());
        result.put("interval", signal.getInterval());
        result.put("generatedAt", signal.getGeneratedAt());
        result.put("createdAt", signal.getCreatedAt());
        result.put("decision", signal.getDecision());
        result.put("originalDecision", signal.getOriginalDecision());
        result.put("confluenceStatus", signal.getConfluenceStatus());
        result.put("confluenceEntryAllowed", signal.isConfluenceEntryAllowed());
        result.put("confluenceHigherInterval", signal.getConfluenceHigherInterval());
        result.put("confluenceHigherDecision", signal.getConfluenceHigherDecision());
        result.put("confluenceHigherTrendScore", signal.getConfluenceHigherTrendScore());
        result.put("confluenceExplanation", signal.getConfluenceExplanation());
        result.put("confluenceEvaluatedAt", signal.getConfluenceEvaluatedAt());
        result.put("confluenceHigherSignalGeneratedAt", signal.getConfluenceHigherSignalGeneratedAt());
        result.put("btcRelationshipType", signal.getBtcRelationshipType());
        result.put("btcContextStatus", signal.getBtcContextStatus());
        result.put("btcContextEntryAllowed", signal.isBtcContextEntryAllowed());
        result.put("btcContextInterval", signal.getBtcContextInterval());
        result.put("btcContextDecision", signal.getBtcContextDecision());
        result.put("btcContextTrendScore", signal.getBtcContextTrendScore());
        result.put("btcCorrelation", signal.getBtcCorrelation());
        result.put("btcBeta", signal.getBtcBeta());
        result.put("btcRelationshipSampleSize", signal.getBtcRelationshipSampleSize());
        result.put("btcInfluenceFactor", signal.getBtcInfluenceFactor());
        result.put("btcRelationshipStable", signal.isBtcRelationshipStable());
        result.put("btcContextExplanation", signal.getBtcContextExplanation());
        result.put("btcContextEvaluatedAt", signal.getBtcContextEvaluatedAt());
        result.put("btcSignalGeneratedAt", signal.getBtcSignalGeneratedAt());
        result.put("derivativesStatus", signal.getDerivativesStatus());
        result.put("derivativesEntryAllowed", signal.isDerivativesEntryAllowed());
        result.put("fundingRate", signal.getFundingRate());
        result.put("fundingPercentile", signal.getFundingPercentile());
        result.put("openInterest", signal.getOpenInterest());
        result.put("openInterestValue", signal.getOpenInterestValue());
        result.put("openInterestChangePercent", signal.getOpenInterestChangePercent());
        result.put("derivativesPriceChangePercent", signal.getDerivativesPriceChangePercent());
        result.put("fundingSampleSize", signal.getFundingSampleSize());
        result.put("derivativesPeriod", signal.getDerivativesPeriod());
        result.put("derivativesConfidenceAdjustment", signal.getDerivativesConfidenceAdjustment());
        result.put("derivativesExplanation", signal.getDerivativesExplanation());
        result.put("derivativesEvaluatedAt", signal.getDerivativesEvaluatedAt());
        result.put("liquidityStatus", signal.getLiquidityStatus());
        result.put("liquidityEntryAllowed", signal.isLiquidityEntryAllowed());
        result.put("orderBookImbalance", signal.getOrderBookImbalance());
        result.put("orderBookBidDepth", signal.getOrderBookBidDepth());
        result.put("orderBookAskDepth", signal.getOrderBookAskDepth());
        result.put("orderBookSpreadPercent", signal.getOrderBookSpreadPercent());
        result.put("nearestBidWallPrice", signal.getNearestBidWallPrice());
        result.put("nearestBidWallSize", signal.getNearestBidWallSize());
        result.put("nearestAskWallPrice", signal.getNearestAskWallPrice());
        result.put("nearestAskWallSize", signal.getNearestAskWallSize());
        result.put("orderBookTargetBlocked", signal.isOrderBookTargetBlocked());
        result.put("orderBookStopExposed", signal.isOrderBookStopExposed());
        result.put("orderBookObservations", signal.getOrderBookObservations());
        result.put("liquidityExplanation", signal.getLiquidityExplanation());
        result.put("liquidityEvaluatedAt", signal.getLiquidityEvaluatedAt());
        result.put("marketRegime", signal.getMarketRegime());
        result.put("marketRegimeConfidence", signal.getMarketRegimeConfidence());
        result.put("selectedStrategy", signal.getSelectedStrategy());
        result.put("strategyVersion", signal.getStrategyVersion());
        result.put("strategyEntryAllowed", signal.isStrategyEntryAllowed());
        result.put("strategyExplanation", signal.getStrategyExplanation());
        result.put("strategyTrendMaximum", signal.getStrategyTrendMaximum());
        result.put("strategyVolumeMaximum", signal.getStrategyVolumeMaximum());
        result.put("strategyMomentumMaximum", signal.getStrategyMomentumMaximum());
        result.put("strategySentimentMaximum", signal.getStrategySentimentMaximum());
        result.put("strategyFundamentalMaximum", signal.getStrategyFundamentalMaximum());
        result.put("strategyBreakdown", parseAnalysisBreakdown(signal.getStrategyBreakdown()));
        result.put("marketContextSnapshot", parseAnalysisBreakdown(signal.getMarketContextSnapshot()));
        result.put("totalScore", signal.getTotalScore());
        result.put("confidenceScore", signal.getConfidenceScore());
        // FIX-091 / Fix 3-5: expose the real confidence and authority used by the pre-wallet engine.
        result.put("rawConfidenceScore", signal.getRawConfidenceScore());
        result.put("effectiveConfidenceScore", signal.getEffectiveConfidenceScore());
        result.put("primaryBlockingStage", signal.getPrimaryBlockingStage());
        result.put("detectedRegime", signal.getDetectedRegime());
        result.put("candidateRegime", signal.getCandidateRegime());
        result.put("confirmedRegime", signal.getConfirmedRegime());
        result.put("regimeCandidateCount", signal.getRegimeCandidateCount());
        result.put("entryAuthority", signal.getEntryAuthority());
        result.put("entryAuthorityMaxPositionPercent", signal.getEntryAuthorityMaxPositionPercent());
        result.put("entryAuthorityExplanation", signal.getEntryAuthorityExplanation());
        result.put("finalEntryAllowed", signal.isFinalEntryAllowed());
        result.put("decisionPath", parseDecisionPath(signal.getDecisionPath()));
        result.put("finalDecisionExplanation", signal.getFinalDecisionExplanation());
        result.put("trendScore", signal.getTrendScore());
        result.put("volumeScore", signal.getVolumeScore());
        result.put("momentumScore", signal.getMomentumScore());
        result.put("sentimentScore", signal.getSentimentScore());
        result.put("fundamentalScore", signal.getFundamentalScore());
        result.put("rawScore", signal.getRawScore());
        result.put("maximumAvailableScore", signal.getMaximumAvailableScore());
        result.put("sentimentAvailable", signal.isSentimentAvailable());
        result.put("fundamentalAvailable", signal.isFundamentalAvailable());
        result.put("excludedCategories", parseAnalysisBreakdown(signal.getExcludedCategories()));
        Map<String, Object> analysisDetails = parseAnalysisBreakdown(signal.getAnalysisBreakdown());
        result.put("scoreBreakdown", Map.of(
                "movingAverages", Map.of(
                        "score", signal.getTrendScore(), "maximum", 25,
                        "components", List.of(
                                component("Trend Direction", signal.getTrendDirectionScore(), 8, nestedDetail(analysisDetails, "trendGroups", "direction")),
                                component("Trend Structure", signal.getTrendStructureScore(), 7, nestedDetail(analysisDetails, "trendGroups", "structure")),
                                component("Trend Strength", signal.getTrendStrengthScore(), 6, nestedDetail(analysisDetails, "trendGroups", "strength")),
                                component("Price Location", signal.getTrendPriceLocationScore(), 4, nestedDetail(analysisDetails, "trendGroups", "priceLocation"))
                        )),
                "momentum", Map.of(
                        "score", signal.getMomentumScore(), "maximum", 15,
                        "components", List.of(
                                component("RSI", signal.getRsiScore(), 7, analysisDetails.get("rsi")),
                                component("MACD", signal.getMacdScore(), 8, analysisDetails.get("macd"))
                        )),
                "bandsVolume", Map.of(
                        "score", signal.getVolumeScore(), "maximum", 20,
                        "components", List.of(
                                component("Bollinger Bands", signal.getBollingerScore(), 6, analysisDetails.get("bollinger")),
                                component("Relative Volume", signal.getRelativeVolumeScore(), 8, analysisDetails.get("relativeVolume")),
                                component("Volume SMA20", signal.getVolumeSma20Score(), 6, analysisDetails.get("volumeSma20"))
                        )),
                "sentiment", Map.of("score", signal.getSentimentScore(), "maximum", 15,
                        "providers", parseSentimentBreakdown(signal.getSentimentBreakdown())),
                "fundamentals", fundamentalBreakdown(signal, analysisDetails.get("fundamentals"))
        ));
        result.put("latestPrice", signal.getLatestPrice());
        result.put("stopLoss", signal.getStopLoss());
        result.put("takeProfit", signal.getTakeProfit());
        result.put("atr14", signal.getAtrAtSignal());
        result.put("atrPercent", signal.getAtrPercent());
        result.put("riskRewardRatio", signal.getRiskRewardRatio());
        result.put("candleRangeAtrMultiple", signal.getCandleRangeAtrMultiple());
        result.put("volatilityLevel", signal.getVolatilityLevel());
        result.put("atrOverextended", signal.isAtrOverextended());
        result.put("atrEntryType", signal.getAtrEntryType());
        result.put("atrRecommendedPositionPercent", signal.getAtrRecommendedPositionPercent());
        result.put("atrImmediateEntryAllowed", signal.isAtrImmediateEntryAllowed());
        result.put("atrRetracementEntryPrice", signal.getAtrRetracementEntryPrice());
        result.put("atrExplanation", signal.getAtrExplanation());
        result.put("explanation", signal.getExplanation());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object nestedDetail(Map<String, Object> source, String parentKey, String childKey) {
        Object parent = source.get(parentKey);
        if (parent instanceof Map<?, ?> parentMap) {
            return ((Map<String, Object>) parentMap).get(childKey);
        }
        return null;
    }

    private Map<String, Object> positionDto(
            PaperPosition position,
            BigDecimal currentMarketPrice,
            TradeSignal latestSignal
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean open = position.getStatus() == PositionStatus.OPEN;
        TradeSignal exitSignal = position.getExitSignal();
        WalletTrade walletExitTrade = (!open && exitSignal != null && exitSignal.getId() != null)
                ? walletTradeRepository
                    .findTopBySignalIdAndSideAndStatusOrderByExecutedAtDesc(
                            exitSignal.getId(), "SELL", "EXECUTED")
                    .orElse(null)
                : null;

        BigDecimal displayPrice = open
                ? currentMarketPrice
                : walletExitTrade != null && walletExitTrade.getPriceUsdt() != null
                    ? walletExitTrade.getPriceUsdt()
                    : position.getExitPrice();

        BigDecimal pnl = open
                ? position.getRealizedPnl()
                : walletExitTrade != null && walletExitTrade.getRealizedPnlUsdt() != null
                    ? walletExitTrade.getRealizedPnlUsdt()
                    : position.getRealizedPnl();

        if (open && displayPrice != null) {
            pnl = displayPrice.subtract(position.getEntryPrice())
                    .multiply(position.getQuantity());
        }

        BigDecimal pnlPercentage;
        if (!open && walletExitTrade != null && walletExitTrade.getRealizedPnlPercent() != null) {
            pnlPercentage = walletExitTrade.getRealizedPnlPercent();
        } else if (displayPrice != null && position.getEntryPrice().signum() != 0) {
            pnlPercentage = displayPrice.subtract(position.getEntryPrice())
                    .divide(position.getEntryPrice(), 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        } else {
            pnlPercentage = BigDecimal.ZERO;
        }

        Instant lifecycleEnd = open || position.getClosedAt() == null
                ? Instant.now()
                : position.getClosedAt();
        long holdingSeconds = Math.max(0,
                Duration.between(position.getOpenedAt(), lifecycleEnd).getSeconds());

        TradeSignal entrySignal = position.getSignal();
        String currentDecision = latestSignal == null ? "NO_SIGNAL" : latestSignal.getDecision().name();
        Integer currentScore = latestSignal == null ? null : latestSignal.getTotalScore();

        result.put("id", position.getId());
        result.put("symbol", position.getSymbol());
        result.put("side", position.getSide());
        result.put("status", position.getStatus().name());
        BigDecimal displayQuantity = position.getQuantity();
        if (open) {
            WalletManagedPosition liveManaged = walletManagedPositionRepository
                    .findTopBySymbolAndStatusOrderByOpenedAtDesc(position.getSymbol(), "OPEN")
                    .orElse(null);
            if (liveManaged != null && liveManaged.getQuantity() != null && liveManaged.getQuantity().signum() > 0) {
                displayQuantity = liveManaged.getQuantity();
            }
        } else if (walletExitTrade != null && walletExitTrade.getQuantity() != null
                && walletExitTrade.getQuantity().signum() > 0) {
            displayQuantity = walletExitTrade.getQuantity();
        }
        result.put("quantity", displayQuantity);
        result.put("entryPrice", position.getEntryPrice());
        result.put("currentPrice", displayPrice);
        result.put("stopLoss", position.getStopLoss());
        result.put("takeProfit", position.getTakeProfit());
        result.put("exitPrice", open ? position.getExitPrice() : displayPrice);
        result.put("unrealizedPnl", open ? pnl : null);
        result.put("realizedPnl", open ? null : pnl);
        result.put("pnlPercentage", pnlPercentage);
        result.put("openedAt", position.getOpenedAt());
        result.put("closedAt", position.getClosedAt());
        result.put("holdingSeconds", holdingSeconds);
        result.put("entrySignalId", entrySignal == null ? null : entrySignal.getId());
        result.put("entryDecision", entrySignal == null ? null : entrySignal.getDecision().name());
        result.put("entryScore", entrySignal == null ? null : entrySignal.getTotalScore());
        result.put("entryInterval", entrySignal == null ? null : entrySignal.getInterval());
        result.put("entryAtr14", entrySignal == null ? null : entrySignal.getAtrAtSignal());
        result.put("entryAtrPercent", entrySignal == null ? null : entrySignal.getAtrPercent());
        result.put("entryRiskRewardRatio", entrySignal == null ? null : entrySignal.getRiskRewardRatio());
        result.put("entryVolatilityLevel", entrySignal == null ? null : entrySignal.getVolatilityLevel());
        result.put("stopLossDistance", position.getEntryPrice().subtract(position.getStopLoss()).abs());
        result.put("takeProfitDistance", position.getTakeProfit().subtract(position.getEntryPrice()).abs());
        result.put("stopLossPercent", percentageDistance(position.getEntryPrice(), position.getStopLoss()));
        result.put("takeProfitPercent", percentageDistance(position.getEntryPrice(), position.getTakeProfit()));
        result.put("currentToStopDistance", open && displayPrice != null
                ? displayPrice.subtract(position.getStopLoss()) : null);
        result.put("currentToTargetDistance", open && displayPrice != null
                ? position.getTakeProfit().subtract(displayPrice) : null);
        result.put("stopLossTriggered", open && displayPrice != null
                && displayPrice.compareTo(position.getStopLoss()) <= 0);
        result.put("takeProfitTriggered", open && displayPrice != null
                && displayPrice.compareTo(position.getTakeProfit()) >= 0);
        WalletManagedPosition managedPosition = open
                ? walletManagedPositionRepository.findTopBySymbolAndStatusOrderByOpenedAtDesc(position.getSymbol(), "OPEN").orElse(null)
                : null;
        result.put("profitLockActive", managedPosition != null && managedPosition.isProfitLockActive());
        result.put("profitLockPrice", managedPosition == null ? null : managedPosition.getProfitLockPriceUsdt());
        result.put("profitLockProgressPercent", managedPosition == null ? null : managedPosition.getProfitLockProgressPercent());
        result.put("highestPriceSinceEntry", managedPosition == null ? null : managedPosition.getHighestPriceUsdt());
        result.put("riskLogic", entrySignal == null
                ? "Stored stop-loss and take-profit levels"
                : atrRiskLogic(entrySignal));
        result.put("entryReason", position.getEntryReason() != null
                ? position.getEntryReason()
                : entrySignal == null ? null : entrySignal.getExplanation());
        result.put("exitSignalId", exitSignal == null ? null : exitSignal.getId());
        result.put("exitDecision", exitSignal == null ? null : exitSignal.getDecision().name());
        result.put("exitScore", exitSignal == null ? null : exitSignal.getTotalScore());
        result.put("exitInterval", exitSignal == null ? null : exitSignal.getInterval());
        result.put("exitReason", position.getExitReason());
        result.put("closeReason", position.getCloseReason());
        result.put("currentDecision", open ? currentDecision : null);
        result.put("currentScore", open ? currentScore : null);
        result.put("recommendation", open
                ? recommendation(currentDecision)
                : "Trade completed");
        result.put("predictionResult", open || pnl == null
                ? "PENDING"
                : pnl.signum() > 0 ? "SUCCESS"
                : pnl.signum() < 0 ? "FAILED" : "BREAKEVEN");
        return result;
    }


    private BigDecimal percentageDistance(BigDecimal entryPrice, BigDecimal level) {
        if (entryPrice == null || level == null || entryPrice.signum() == 0) {
            return null;
        }
        return level.subtract(entryPrice).abs()
                .divide(entryPrice, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private String atrRiskLogic(TradeSignal signal) {
        if (signal.getAtrAtSignal() == null) {
            return "ATR was unavailable when this position opened; stored fallback levels were used.";
        }
        return "Stop loss = entry price - configured ATR multiple; take profit = entry price + configured ATR multiple. "
                + "ATR14 at entry was " + signal.getAtrAtSignal()
                + " and risk/reward was 1:" + signal.getRiskRewardRatio() + ".";
    }

    private String recommendation(String decision) {
        return switch (decision) {
            case "STRONG_BUY", "BUY" -> "Hold the existing position; do not open a duplicate trade.";
            case "WATCH" -> "Continue holding while waiting for confirmation.";
            case "NEUTRAL" -> "Hold cautiously; signals are mixed.";
            case "SELL", "STRONG_SELL" -> "Exit is expected on this signal.";
            default -> "Waiting for the next analysis.";
        };
    }



    private Map<String, Object> fundamentalBreakdown(TradeSignal signal, Object details) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", signal.getFundamentalScore());
        result.put("maximum", 10);
        result.put("riskLevel", "UNKNOWN");
        result.put("components", List.of());
        if (details instanceof Map<?, ?> detailMap) {
            detailMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        }
        return result;
    }

    private Map<String, Object> component(String label, int score, int maximum, Object details) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("label", label);
        result.put("score", score);
        result.put("maximum", maximum);
        if (details instanceof Map<?, ?> detailMap) {
            detailMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        }
        return result;
    }

    private Map<String, Object> parseAnalysisBreakdown(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            return Map.of();
        }
    }
    private List<Map<String, Object>> parseDecisionPath(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<Map<String, Object>> parseSentimentBreakdown(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }
}
