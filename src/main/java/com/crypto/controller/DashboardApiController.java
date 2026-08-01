package com.crypto.controller;

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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public DashboardApiController(
            CandleRepository candleRepository,
            TechnicalIndicatorRepository technicalIndicatorRepository,
            TradeSignalRepository tradeSignalRepository,
            PaperPositionRepository paperPositionRepository,
            SentimentService sentimentService,
            ScheduleConfigurationService scheduleConfigurationService,
            ObjectMapper objectMapper,
            ScoreDiagnosticsService scoreDiagnosticsService
    ) {
        this.candleRepository = candleRepository;
        this.technicalIndicatorRepository = technicalIndicatorRepository;
        this.tradeSignalRepository = tradeSignalRepository;
        this.paperPositionRepository = paperPositionRepository;
        this.sentimentService = sentimentService;
        this.scheduleConfigurationService = scheduleConfigurationService;
        this.objectMapper = objectMapper;
        this.scoreDiagnosticsService = scoreDiagnosticsService;
    }

    @GetMapping("/symbols")
    public List<String> symbols() {
        List<String> symbols = candleRepository.findDistinctSymbols();
        return symbols.isEmpty() ? List.of("BTCUSDT") : symbols;
    }

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public Map<String, Object> overview(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "1m") String interval
    ) {
        String normalizedSymbol = symbol.trim().toUpperCase();
        String normalizedInterval = interval.trim();

        List<Candle> candles = candleRepository.findClosedCandles(
                normalizedSymbol,
                normalizedInterval,
                PageRequest.of(0, 120)
        );
        Collections.reverse(candles);

        TechnicalIndicator latestIndicator = technicalIndicatorRepository
                .findTopBySymbolAndIntervalCodeOrderByCandleOpenTimeDesc(normalizedSymbol, normalizedInterval)
                .orElse(null);

        TradeSignal latestSignal = tradeSignalRepository
                .findTopBySymbolOrderByGeneratedAtDesc(normalizedSymbol)
                .orElse(null);

        List<TradeSignal> signals = tradeSignalRepository
                .findTop20BySymbolOrderByGeneratedAtDesc(normalizedSymbol);

        List<PaperPosition> positions = paperPositionRepository
                .findTop20BySymbolOrderByOpenedAtDesc(normalizedSymbol);

        SentimentOverview sentiment = sentimentService.overview(normalizedSymbol);

        long closedCandleCount = candleRepository.countBySymbolAndIntervalCodeAndClosedTrue(
                normalizedSymbol,
                normalizedInterval
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("symbol", normalizedSymbol);
        response.put("interval", normalizedInterval);
        response.put("updatedAt", Instant.now());
        response.put("summary", summary(candles, latestIndicator, latestSignal, positions, closedCandleCount));
        response.put("pipeline", pipeline(closedCandleCount, latestIndicator, latestSignal, positions));
        response.put("candles", candles.stream().map(this::candleDto).toList());
        response.put("indicator", indicatorDto(latestIndicator));
        response.put("sentiment", sentiment);
        response.put("schedule", scheduleConfigurationService.dashboardSchedule());
        response.put("scoreDiagnostics", scoreDiagnosticsService.last24Hours());
        response.put("signals", signals.stream().map(this::signalDto).toList());
        BigDecimal currentPrice = candles.isEmpty()
                ? (latestSignal == null ? null : latestSignal.getLatestPrice())
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

    private Map<String, Object> summary(
            List<Candle> candles,
            TechnicalIndicator indicator,
            TradeSignal signal,
            List<PaperPosition> positions,
            long closedCandleCount
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
        result.put("minimumCandles", 210);
        result.put("indicatorReady", indicator != null);
        result.put("latestDecision", signal == null ? "NO_SIGNAL" : signal.getDecision().name());
        result.put("latestScore", signal == null ? null : signal.getTotalScore());
        result.put("openPositions", openPositions);
        return result;
    }

    private Map<String, Object> pipeline(
            long closedCandleCount,
            TechnicalIndicator indicator,
            TradeSignal signal,
            List<PaperPosition> positions
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candle", status(closedCandleCount > 0,
                closedCandleCount + " closed candles"));
        result.put("indicator", status(indicator != null,
                indicator == null
                        ? "Waiting for 210 closed candles or next close event"
                        : "Saved at " + indicator.getCandleOpenTime()));
        result.put("marketContext", status(signal != null,
                signal == null ? "Waiting for analysis" : signal.getMarketRegime().name()));
        result.put("strategy", status(signal != null,
                signal == null ? "Waiting for market context" : signal.getSelectedStrategy().name()));
        result.put("derivatives", status(signal != null,
                signal == null ? "Waiting for signal" : signal.getDerivativesStatus().name()));
        result.put("orderBook", status(signal != null,
                signal == null ? "Collecting depth" : signal.getLiquidityStatus().name()));
        result.put("analysis", status(signal != null,
                signal == null ? "No persisted trade signal yet" : signal.getDecision().name()));
        result.put("paperTrading", status(!positions.isEmpty(),
                positions.isEmpty() ? "No paper position created yet" : positions.get(0).getStatus().name()));
        return result;
    }

    private Map<String, Object> status(boolean complete, String detail) {
        return Map.of("complete", complete, "detail", detail);
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
        result.put("riskLogic", entrySignal == null
                ? "Stored stop-loss and take-profit levels"
                : atrRiskLogic(entrySignal));
        result.put("entryReason", position.getEntryReason() != null
                ? position.getEntryReason()
                : entrySignal == null ? null : entrySignal.getExplanation());
        result.put("exitSignalId", exitSignal == null ? null : exitSignal.getId());
        result.put("exitDecision", exitSignal == null ? null : exitSignal.getDecision().name());
        result.put("exitScore", exitSignal == null ? null : exitSignal.getTotalScore());
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
