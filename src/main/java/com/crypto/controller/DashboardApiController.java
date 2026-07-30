package com.crypto.controller;

import com.crypto.domain.Candle;
import com.crypto.domain.PaperPosition;
import com.crypto.domain.PositionStatus;
import com.crypto.domain.TechnicalIndicator;
import com.crypto.domain.TradeSignal;
import com.crypto.dto.SentimentOverview;
import com.crypto.service.SentimentService;
import com.crypto.service.ScheduleConfigurationService;
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

    public DashboardApiController(
            CandleRepository candleRepository,
            TechnicalIndicatorRepository technicalIndicatorRepository,
            TradeSignalRepository tradeSignalRepository,
            PaperPositionRepository paperPositionRepository,
            SentimentService sentimentService,
            ScheduleConfigurationService scheduleConfigurationService
    ) {
        this.candleRepository = candleRepository;
        this.technicalIndicatorRepository = technicalIndicatorRepository;
        this.tradeSignalRepository = tradeSignalRepository;
        this.paperPositionRepository = paperPositionRepository;
        this.sentimentService = sentimentService;
        this.scheduleConfigurationService = scheduleConfigurationService;
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
        response.put("signals", signals.stream().map(this::signalDto).toList());
        response.put("positions", positions.stream().map(this::positionDto).toList());
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
        result.put("generatedAt", signal.getGeneratedAt());
        result.put("createdAt", signal.getCreatedAt());
        result.put("decision", signal.getDecision());
        result.put("totalScore", signal.getTotalScore());
        result.put("trendScore", signal.getTrendScore());
        result.put("volumeScore", signal.getVolumeScore());
        result.put("momentumScore", signal.getMomentumScore());
        result.put("sentimentScore", signal.getSentimentScore());
        result.put("fundamentalScore", signal.getFundamentalScore());
        result.put("latestPrice", signal.getLatestPrice());
        result.put("stopLoss", signal.getStopLoss());
        result.put("takeProfit", signal.getTakeProfit());
        result.put("explanation", signal.getExplanation());
        return result;
    }

    private Map<String, Object> positionDto(PaperPosition position) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", position.getId());
        result.put("side", position.getSide());
        result.put("status", position.getStatus());
        result.put("quantity", position.getQuantity());
        result.put("entryPrice", position.getEntryPrice());
        result.put("stopLoss", position.getStopLoss());
        result.put("takeProfit", position.getTakeProfit());
        result.put("exitPrice", position.getExitPrice());
        result.put("realizedPnl", position.getRealizedPnl());
        result.put("openedAt", position.getOpenedAt());
        result.put("closedAt", position.getClosedAt());
        result.put("signalId", position.getSignal() == null ? null : position.getSignal().getId());
        return result;
    }
}
