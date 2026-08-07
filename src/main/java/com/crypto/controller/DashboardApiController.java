package com.crypto.controller;

import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.domain.Candle;
import com.crypto.domain.PaperPosition;
import com.crypto.domain.PositionStatus;
import com.crypto.domain.TechnicalIndicator;
import com.crypto.domain.TradeSignal;
import com.crypto.dto.SentimentOverview;
import com.crypto.service.SentimentService;
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
    private final SentimentService sentimentService;
    private final ScheduleConfigurationService scheduleConfigurationService;
    private final ObjectMapper objectMapper;
    private final ScoreDiagnosticsService scoreDiagnosticsService;
    private final CoinConfigurationService coinConfigurationService;
    private final WalletTradeRepository walletTradeRepository;
    private final WalletManagedPositionRepository walletManagedPositionRepository;
    private final Map<String, AggregatedCandleCacheEntry> aggregatedCandleCache = new ConcurrentHashMap<>();

    public DashboardApiController(
            CandleRepository candleRepository,
            TechnicalIndicatorRepository technicalIndicatorRepository,
            TradeSignalRepository tradeSignalRepository,
            PaperPositionRepository paperPositionRepository,
            SentimentService sentimentService,
            ScheduleConfigurationService scheduleConfigurationService,
            ObjectMapper objectMapper,
            ScoreDiagnosticsService scoreDiagnosticsService,
            CoinConfigurationService coinConfigurationService,
            WalletTradeRepository walletTradeRepository,
            WalletManagedPositionRepository walletManagedPositionRepository
    ) {
        this.candleRepository = candleRepository;
        this.technicalIndicatorRepository = technicalIndicatorRepository;
        this.tradeSignalRepository = tradeSignalRepository;
        this.paperPositionRepository = paperPositionRepository;
        this.sentimentService = sentimentService;
        this.scheduleConfigurationService = scheduleConfigurationService;
        this.objectMapper = objectMapper;
        this.scoreDiagnosticsService = scoreDiagnosticsService;
        this.coinConfigurationService = coinConfigurationService;
        this.walletTradeRepository = walletTradeRepository;
        this.walletManagedPositionRepository = walletManagedPositionRepository;
    }

    @GetMapping("/symbols")
    public List<String> symbols() {
        List<String> symbols = coinConfigurationService.enabledSymbols();
        return symbols.isEmpty() ? List.of("BTCUSDT") : symbols;
    }

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public Map<String, Object> overview(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "1m") String interval
    ) {
        String normalizedSymbol = symbol.trim().toUpperCase();
        String normalizedInterval = interval.trim().toLowerCase();
        boolean displayOnlyInterval = isDisplayOnlyInterval(normalizedInterval);

        List<Candle> candles = displayOnlyInterval
                ? loadAggregatedCandles(normalizedSymbol, normalizedInterval, 120)
                : loadClosedCandles(normalizedSymbol, normalizedInterval, 120);

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

        SentimentOverview sentiment = sentimentService.overview(normalizedSymbol);
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
        response.put("indicator", indicatorDto(latestIndicator));
        response.put("sentiment", sentiment);
        response.put("schedule", scheduleConfigurationService.dashboardSchedule());
        response.put("scoreDiagnostics", scoreDiagnosticsService.last24Hours());
        response.put("signals", signals.stream().map(this::signalDto).toList());
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
        return response;
    }

    private boolean isDisplayOnlyInterval(String interval) {
        return "4h".equals(interval) || "1d".equals(interval);
    }

    private List<Candle> loadClosedCandles(String symbol, String interval, int limit) {
        List<Candle> candles = candleRepository.findClosedCandles(symbol, interval, PageRequest.of(0, limit));
        Collections.reverse(candles);
        return candles;
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
        result.put("open", candle.getOpenPrice());
        result.put("high", candle.getHighPrice());
        result.put("low", candle.getLowPrice());
        result.put("close", candle.getClosePrice());
        result.put("volume", candle.getVolume());
        return result;
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
        BigDecimal displayPrice = open ? currentMarketPrice : position.getExitPrice();
        BigDecimal pnl = position.getRealizedPnl();

        if (open && displayPrice != null) {
            pnl = displayPrice.subtract(position.getEntryPrice())
                    .multiply(position.getQuantity());
        }

        BigDecimal pnlPercentage = BigDecimal.ZERO;
        if (displayPrice != null && position.getEntryPrice().signum() != 0) {
            pnlPercentage = displayPrice.subtract(position.getEntryPrice())
                    .divide(position.getEntryPrice(), 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        Instant lifecycleEnd = open || position.getClosedAt() == null
                ? Instant.now()
                : position.getClosedAt();
        long holdingSeconds = Math.max(0,
                Duration.between(position.getOpenedAt(), lifecycleEnd).getSeconds());

        TradeSignal entrySignal = position.getSignal();
        TradeSignal exitSignal = position.getExitSignal();
        String currentDecision = latestSignal == null ? "NO_SIGNAL" : latestSignal.getDecision().name();
        Integer currentScore = latestSignal == null ? null : latestSignal.getTotalScore();

        result.put("id", position.getId());
        result.put("symbol", position.getSymbol());
        result.put("side", position.getSide());
        result.put("status", position.getStatus().name());
        result.put("quantity", position.getQuantity());
        result.put("entryPrice", position.getEntryPrice());
        result.put("currentPrice", displayPrice);
        result.put("stopLoss", position.getStopLoss());
        result.put("takeProfit", position.getTakeProfit());
        result.put("exitPrice", position.getExitPrice());
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
