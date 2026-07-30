package com.crypto.service;

import com.crypto.domain.MarketFundamental;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TechnicalIndicator;
import com.crypto.domain.TradeSignal;
import com.crypto.dto.IndicatorSnapshot;
import com.crypto.indicator.service.TechnicalIndicatorService;
import com.crypto.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private static final MathContext MC = MathContext.DECIMAL64;

    private static final int MAX_TREND_SCORE = 25;
    private static final int MAX_VOLUME_SCORE = 20;
    private static final int MAX_MOMENTUM_SCORE = 15;
    private static final int MAX_SENTIMENT_SCORE = 15;
    private static final int MAX_FUNDAMENTAL_SCORE = 10;

    private final TechnicalIndicatorService technicalIndicatorService;
    private final SentimentService sentimentService;
    private final FundamentalService fundamentalService;
    private final TradeSignalRepository signalRepository;

    /**
     * Manual entry point used by controllers or recovery jobs.
     * It reads the latest already-persisted technical indicator.
     */
    @Transactional
    public TradeSignal analyze(String symbol, String interval) {
        TechnicalIndicator indicator = technicalIndicatorService
                .getLatest(symbol, interval)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No technical indicator found for " + symbol + " " + interval
                ));

        return analyze(indicator);
    }

    /**
     * Automatic entry point. It receives the TechnicalIndicator row that was
     * just saved by TechnicalIndicatorService and never recalculates candles.
     */
    @Transactional
    public TradeSignal analyze(TechnicalIndicator indicator) {
        IndicatorSnapshot i = toSnapshot(indicator);
        String symbol = i.symbol();

        boolean sentimentEnabled = sentimentService.isEnabled();
        BigDecimal sentiment = sentimentService.currentScore(symbol);
        MarketFundamental fundamental = fundamentalService.latest(symbol).orElse(null);

        int trend = trendScore(i);
        int volume = volumeScore(i);
        int momentum = momentumScore(i);
        int sentimentPoints = sentimentEnabled ? sentimentScore(sentiment) : 0;
        int fundamentals = fundamentalScore(fundamental);

        int rawTotal = trend + volume + momentum + sentimentPoints + fundamentals;
        int maximumAvailableScore = MAX_TREND_SCORE
                + MAX_VOLUME_SCORE
                + MAX_MOMENTUM_SCORE
                + MAX_FUNDAMENTAL_SCORE
                + (sentimentEnabled ? MAX_SENTIMENT_SCORE : 0);
        int total = normalizeScore(rawTotal, maximumAvailableScore);

        BigDecimal stopLoss = i.latestPrice()
                .subtract(i.atr14().multiply(BigDecimal.valueOf(1.5), MC), MC);
        BigDecimal takeProfit = i.latestPrice().add(
                i.latestPrice()
                        .subtract(stopLoss, MC)
                        .multiply(BigDecimal.valueOf(2), MC),
                MC
        );

        SignalDecision decision = decision(total);
        String explanation = explanation(
                i,
                sentiment,
                fundamental,
                trend,
                volume,
                momentum,
                sentimentPoints,
                fundamentals,
                sentimentEnabled,
                rawTotal,
                maximumAvailableScore,
                total
        );

        return signalRepository.save(TradeSignal.builder()
                .symbol(symbol)
                .interval(i.intervalCode())
                .decision(decision)
                .totalScore(total)
                .trendScore(trend)
                .volumeScore(volume)
                .momentumScore(momentum)
                .sentimentScore(sentimentPoints)
                .fundamentalScore(fundamentals)
                .latestPrice(i.latestPrice())
                .stopLoss(stopLoss.max(BigDecimal.ZERO))
                .takeProfit(takeProfit)
                .explanation(explanation)
                .generatedAt(Instant.now())
                .build());
    }

    private IndicatorSnapshot toSnapshot(TechnicalIndicator indicator) {
        if (indicator == null) {
            throw new IllegalArgumentException("Technical indicator is required");
        }

        return new IndicatorSnapshot(
                indicator.getSymbol(),
                indicator.getIntervalCode(),
                indicator.getCandleOpenTime(),
                indicator.getClosePrice(),
                indicator.getSma20(),
                indicator.getEma20(),
                indicator.getEma50(),
                indicator.getEma200(),
                indicator.getRsi14(),
                indicator.getMacd(),
                indicator.getMacdSignal(),
                indicator.getMacdHistogram(),
                indicator.getBollingerMiddle(),
                indicator.getBollingerUpper(),
                indicator.getBollingerLower(),
                indicator.getBollingerBandwidth(),
                indicator.getAtr14(),
                indicator.getVolumeSma20(),
                indicator.getRelativeVolume()
        );
    }

    private int trendScore(IndicatorSnapshot i) {
        int score = 0;
        if (i.latestPrice().compareTo(i.ema20()) > 0) score += 5;
        if (i.ema20().compareTo(i.ema50()) > 0) score += 8;
        if (i.ema50().compareTo(i.ema200()) > 0) score += 8;
        if (i.latestPrice().compareTo(i.ema200()) > 0) score += 4;
        return score;
    }

    private int volumeScore(IndicatorSnapshot i) {
        BigDecimal rvol = i.relativeVolume();
        if (rvol.compareTo(BigDecimal.valueOf(2)) >= 0) return 20;
        if (rvol.compareTo(BigDecimal.valueOf(1.5)) >= 0) return 16;
        if (rvol.compareTo(BigDecimal.ONE) >= 0) return 11;
        if (rvol.compareTo(BigDecimal.valueOf(0.7)) >= 0) return 6;
        return 2;
    }

    private int momentumScore(IndicatorSnapshot i) {
        int score = 0;
        if (i.rsi14().compareTo(BigDecimal.valueOf(50)) >= 0
                && i.rsi14().compareTo(BigDecimal.valueOf(70)) <= 0) {
            score += 8;
        } else if (i.rsi14().compareTo(BigDecimal.valueOf(40)) >= 0
                && i.rsi14().compareTo(BigDecimal.valueOf(75)) <= 0) {
            score += 4;
        }

        if (i.macd().compareTo(i.macdSignal()) > 0) score += 7;
        return score;
    }

    private int sentimentScore(BigDecimal sentiment) {
        double normalized = Math.max(-1, Math.min(1, sentiment.doubleValue()));
        return (int) Math.round((normalized + 1) * 7.5);
    }

    private int fundamentalScore(MarketFundamental f) {
        if (f == null || f.getMarketCap() == null || f.getMarketCap().signum() <= 0) {
            return 5;
        }

        int score = 0;
        if (f.getFullyDilutedValuation() != null) {
            BigDecimal ratio = f.getFullyDilutedValuation().divide(f.getMarketCap(), MC);
            if (ratio.compareTo(BigDecimal.valueOf(1.5)) <= 0) score += 6;
            else if (ratio.compareTo(BigDecimal.valueOf(3)) <= 0) score += 3;
        }

        if (f.getVolume24h() != null) {
            BigDecimal volumeRatio = f.getVolume24h().divide(f.getMarketCap(), MC);
            if (volumeRatio.compareTo(BigDecimal.valueOf(0.05)) >= 0) score += 4;
            else if (volumeRatio.compareTo(BigDecimal.valueOf(0.01)) >= 0) score += 2;
        }
        return Math.min(score, 10);
    }

    private int normalizeScore(int rawScore, int maximumAvailableScore) {
        if (maximumAvailableScore <= 0) {
            return 0;
        }

        double normalized = rawScore * 100.0 / maximumAvailableScore;
        return Math.max(0, Math.min(100, (int) Math.round(normalized)));
    }

    private SignalDecision decision(int total) {
        if (total >= 85) return SignalDecision.STRONG_BUY;
        if (total >= 75) return SignalDecision.BUY;
        if (total >= 60) return SignalDecision.WATCH;
        if (total >= 45) return SignalDecision.NEUTRAL;
        return SignalDecision.REJECT;
    }

    private String explanation(
            IndicatorSnapshot i,
            BigDecimal sentiment,
            MarketFundamental fundamental,
            int trend,
            int volume,
            int momentum,
            int sentimentPoints,
            int fundamentals,
            boolean sentimentEnabled,
            int rawTotal,
            int maximumAvailableScore,
            int normalizedTotal
    ) {
        List<String> reasons = new ArrayList<>();
        reasons.add("Trend " + trend + "/25");
        reasons.add("Volume " + volume + "/20; RVOL="
                + i.relativeVolume().setScale(2, java.math.RoundingMode.HALF_UP));
        reasons.add("Momentum " + momentum + "/15; RSI="
                + i.rsi14().setScale(2, java.math.RoundingMode.HALF_UP));
        if (sentimentEnabled) {
            reasons.add("Sentiment " + sentimentPoints + "/15; raw="
                    + sentiment.setScale(3, java.math.RoundingMode.HALF_UP));
        } else {
            reasons.add("Sentiment disabled; technical/fundamental score normalized to 100");
        }
        reasons.add("Fundamentals " + fundamentals + "/10");
        reasons.add("Raw score " + rawTotal + "/" + maximumAvailableScore
                + "; normalized=" + normalizedTotal + "/100");
        if (fundamental == null) {
            reasons.add("No recent market-cap/FDV record; neutral default applied");
        }
        return String.join(" | ", reasons);
    }
}
