package com.crypto.service;

import com.crypto.domain.MarketFundamental;
import com.crypto.domain.MarketRegime;
import com.crypto.config.AnalysisScoringProperties;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TechnicalIndicator;
import com.crypto.domain.TradeSignal;
import com.crypto.dto.IndicatorSnapshot;
import com.crypto.dto.FundamentalScoreResult;
import com.crypto.dto.FundamentalComponentScore;
import com.crypto.dto.AtrRiskAssessment;
import com.crypto.dto.ProviderSentiment;
import com.crypto.dto.MultiTimeframeConfluenceResult;
import com.crypto.dto.BtcMarketContextResult;
import com.crypto.dto.OrderBookLiquidityResult;
import com.crypto.dto.SentimentOverview;
import com.crypto.dto.MarketRegimeAssessment;
import com.crypto.dto.StrategyProfile;
import com.crypto.dto.StrategyScoreResult;
import com.crypto.dto.MarketContextSnapshot;
import com.crypto.dto.FinalDecisionResult;
import com.crypto.dto.DerivativesPositioningResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.crypto.indicator.service.TechnicalIndicatorService;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.repository.TechnicalIndicatorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final FundamentalScoringService fundamentalScoringService;
    private final TradeSignalRepository signalRepository;
    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final ObjectMapper objectMapper;
    private final AtrRiskService atrRiskService;
    private final AnalysisScoringProperties scoringProperties;
    private final MarketRegimeService marketRegimeService;
    private final MarketStrategyService marketStrategyService;
    private final MarketContextService marketContextService;
    private final MultiTimeframeConfluenceService multiTimeframeConfluenceService;
    private final BtcMarketContextService btcMarketContextService;
    private final OrderBookLiquidityService orderBookLiquidityService;
    private final DerivativesPositioningService derivativesPositioningService;
    private final FinalDecisionService finalDecisionService;

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
        SentimentOverview sentimentOverview = sentimentService.overview(symbol);
        BigDecimal sentiment = sentimentOverview.weightedScore();
        MarketFundamental fundamental = fundamentalService.latest(symbol).orElse(null);

        IndicatorSnapshot previous = previousSnapshot(i);
        MovingAverageBreakdown movingAverages = movingAverageScore(i, previous);
        MomentumBreakdown momentumBreakdown = momentumScore(i);
        BandsVolumeBreakdown bandsVolume = bandsVolumeScore(i);
        int trend = movingAverages.total();
        int volume = bandsVolume.total();
        int momentum = momentumBreakdown.total();
        int sentimentPoints = sentimentEnabled ? sentimentScore(sentiment) : 0;
        FundamentalScoreResult fundamentalBreakdown = fundamentalScoringService.score(fundamental);
        int fundamentals = fundamentalBreakdown.total();

        int baseRawTotal = trend + volume + momentum + sentimentPoints + fundamentals;
        int baseMaximumAvailableScore = MAX_TREND_SCORE
                + MAX_VOLUME_SCORE
                + MAX_MOMENTUM_SCORE
                + MAX_FUNDAMENTAL_SCORE
                + (sentimentEnabled ? MAX_SENTIMENT_SCORE : 0);

        AtrRiskAssessment atrRisk = atrRiskService.assess(i);
        Instant signalGeneratedAt = Instant.now();
        MarketRegimeAssessment regimeAssessment = marketRegimeService.assess(i);
        MarketRegime marketRegime = regimeAssessment.regime();
        MarketContextSnapshot marketContext = marketContextService.build(
                i, atrRisk, sentimentOverview, sentimentEnabled, signalGeneratedAt);
        StrategyProfile strategyProfile = marketStrategyService.select(regimeAssessment, marketContext);
        StrategyScoreResult strategyScore = marketStrategyService.score(
                strategyProfile,
                trend,
                volume,
                momentum,
                sentimentPoints,
                fundamentals,
                sentimentEnabled
        );

        int rawTotal = strategyScore.rawScore();
        int maximumAvailableScore = strategyScore.maximumScore();
        int total = strategyScore.normalizedScore();
        SignalDecision baseDecision = strategyScore.decision();
        SignalDecision atrAdjustedDecision = baseDecision;
        // ATR is a risk/quality filter, not a directional indicator.
        // Do not enter an already overextended BUY; wait for a pullback.
        if (atrRisk.overextended()
                && (atrAdjustedDecision == SignalDecision.BUY || atrAdjustedDecision == SignalDecision.STRONG_BUY)) {
            atrAdjustedDecision = SignalDecision.WATCH;
        }

        MultiTimeframeConfluenceResult confluence = multiTimeframeConfluenceService.evaluate(
                symbol, i.intervalCode(), atrAdjustedDecision, signalGeneratedAt, strategyProfile.strategy()
        );
        BtcMarketContextResult btcContext = btcMarketContextService.evaluate(
                symbol, i.intervalCode(), confluence.finalDecision(), confluence.entryAllowed(), signalGeneratedAt
        );
        DerivativesPositioningResult derivatives = derivativesPositioningService.evaluate(
                symbol, i.intervalCode(), btcContext.finalDecision(), btcContext.entryAllowed(), signalGeneratedAt
        );
        OrderBookLiquidityResult liquidity = orderBookLiquidityService.evaluate(
                symbol,
                i.intervalCode(),
                derivatives.finalDecision(),
                derivatives.entryAllowed(),
                i.latestPrice(),
                atrRisk.stopLoss(),
                atrRisk.takeProfit(),
                signalGeneratedAt
        );
        FinalDecisionResult finalDecision = finalDecisionService.decide(
                baseDecision,
                atrAdjustedDecision,
                atrRisk,
                strategyProfile,
                regimeAssessment,
                marketContext,
                confluence,
                btcContext,
                derivatives,
                liquidity
        );
        SignalDecision decision = finalDecision.finalDecision();

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
        ) + " | Market regime " + marketRegime + " (confidence " + regimeAssessment.confidence() + "%). "
                + "Selected strategy " + strategyProfile.strategy() + " v" + strategyProfile.version()
                + ": " + strategyProfile.explanation() + ". "
                + "Market context: " + String.join("; ", marketContext.evidence()) + ". "
                + atrRisk.explanation() + " | Multi-timeframe confluence: " + confluence.explanation()
                + " | BTC market context: " + btcContext.explanation()
                + " | Funding/open interest: " + derivatives.explanation()
                + " | Order-book liquidity: " + liquidity.explanation()
                + " | " + finalDecision.explanation();

        return signalRepository.save(TradeSignal.builder()
                .symbol(symbol)
                .interval(i.intervalCode())
                .decision(decision)
                .originalDecision(baseDecision)
                .confluenceStatus(confluence.status())
                .confluenceEntryAllowed(confluence.entryAllowed())
                .confluenceHigherInterval(confluence.higherInterval())
                .confluenceHigherDecision(confluence.higherTimeframeDecision())
                .confluenceHigherTrendScore(confluence.higherTimeframeTrendScore())
                .confluenceExplanation(confluence.explanation())
                .confluenceEvaluatedAt(confluence.evaluatedAt())
                .confluenceHigherSignalGeneratedAt(confluence.higherSignalGeneratedAt())
                .btcRelationshipType(btcContext.relationshipType())
                .btcContextStatus(btcContext.contextStatus())
                .btcContextEntryAllowed(btcContext.entryAllowed())
                .btcContextInterval(btcContext.btcInterval())
                .btcContextDecision(btcContext.btcDecision())
                .btcContextTrendScore(btcContext.btcTrendScore())
                .btcCorrelation(btcContext.correlation())
                .btcBeta(btcContext.beta())
                .btcRelationshipSampleSize(btcContext.sampleSize())
                .btcInfluenceFactor(btcContext.influenceFactor())
                .btcRelationshipStable(btcContext.stable())
                .btcContextExplanation(btcContext.explanation())
                .btcContextEvaluatedAt(btcContext.evaluatedAt())
                .btcSignalGeneratedAt(btcContext.btcSignalGeneratedAt())
                .derivativesStatus(derivatives.status())
                .derivativesEntryAllowed(derivatives.entryAllowed())
                .fundingRate(derivatives.fundingRate())
                .fundingPercentile(derivatives.fundingPercentile())
                .openInterest(derivatives.openInterest())
                .openInterestValue(derivatives.openInterestValue())
                .openInterestChangePercent(derivatives.openInterestChangePercent())
                .derivativesPriceChangePercent(derivatives.priceChangePercent())
                .fundingSampleSize(derivatives.fundingSamples())
                .derivativesPeriod(derivatives.futuresPeriod())
                .derivativesConfidenceAdjustment(derivatives.confidenceAdjustment())
                .derivativesExplanation(derivatives.explanation())
                .derivativesEvaluatedAt(derivatives.evaluatedAt())
                .liquidityStatus(liquidity.status())
                .liquidityEntryAllowed(liquidity.entryAllowed())
                .orderBookImbalance(liquidity.imbalance())
                .orderBookBidDepth(liquidity.bidDepth())
                .orderBookAskDepth(liquidity.askDepth())
                .orderBookSpreadPercent(liquidity.spreadPercent())
                .nearestBidWallPrice(liquidity.nearestBidWallPrice())
                .nearestBidWallSize(liquidity.nearestBidWallSize())
                .nearestAskWallPrice(liquidity.nearestAskWallPrice())
                .nearestAskWallSize(liquidity.nearestAskWallSize())
                .orderBookTargetBlocked(liquidity.targetBlocked())
                .orderBookStopExposed(liquidity.stopExposed())
                .orderBookObservations(liquidity.observations())
                .orderBookWindowSeconds(liquidity.windowSeconds())
                .orderBookWallPersistenceSeconds(liquidity.wallPersistenceSeconds())
                .orderBookInfluenceFactor(liquidity.influenceFactor())
                .orderBookVetoAllowed(liquidity.vetoAllowed())
                .liquidityExplanation(liquidity.explanation())
                .liquidityEvaluatedAt(liquidity.evaluatedAt())
                .marketRegime(marketRegime)
                .marketRegimeConfidence(regimeAssessment.confidence())
                .selectedStrategy(strategyProfile.strategy())
                .strategyVersion(strategyProfile.version())
                .strategyEntryAllowed(strategyProfile.entryAllowed())
                .strategyExplanation(strategyProfile.explanation())
                .strategyBreakdown(serializeStrategyBreakdown(
                        regimeAssessment, strategyProfile, strategyScore,
                        trend, volume, momentum, sentimentPoints, fundamentals,
                        baseRawTotal, baseMaximumAvailableScore
                ))
                .marketContextSnapshot(serializeMarketContext(marketContext))
                .strategyTrendMaximum(strategyProfile.trendMaximum())
                .strategyVolumeMaximum(strategyProfile.volumeMaximum())
                .strategyMomentumMaximum(strategyProfile.momentumMaximum())
                .strategySentimentMaximum(sentimentEnabled ? strategyProfile.sentimentMaximum() : 0)
                .strategyFundamentalMaximum(strategyProfile.fundamentalMaximum())
                .totalScore(total)
                .confidenceScore(finalDecision.confidenceScore())
                .finalEntryAllowed(finalDecision.entryAllowed())
                .decisionPath(serializeDecisionPath(finalDecision))
                .finalDecisionExplanation(finalDecision.explanation())
                .trendScore(trend)
                .volumeScore(volume)
                .momentumScore(momentum)
                .sentimentScore(sentimentPoints)
                .fundamentalScore(fundamentals)
                // Legacy component columns remain populated for backward compatibility.
                .emaCrossScore(movingAverages.direction())
                .priceEma200Score(movingAverages.strength())
                .emaAlignmentScore(movingAverages.structure())
                .sma20Score(movingAverages.priceLocation())
                .trendDirectionScore(movingAverages.direction())
                .trendStructureScore(movingAverages.structure())
                .trendStrengthScore(movingAverages.strength())
                .trendPriceLocationScore(movingAverages.priceLocation())
                .rsiScore(momentumBreakdown.rsi())
                .macdScore(momentumBreakdown.macd())
                .bollingerScore(bandsVolume.bollinger())
                .relativeVolumeScore(bandsVolume.relativeVolume())
                .volumeSma20Score(bandsVolume.volumeSma20())
                .rawScore(rawTotal)
                .maximumAvailableScore(maximumAvailableScore)
                .sentimentBreakdown(serializeSentiment(sentimentOverview.providers()))
                .analysisBreakdown(serializeAnalysisBreakdown(
                        i, movingAverages, momentumBreakdown, bandsVolume, fundamentalBreakdown,
                        regimeAssessment, strategyProfile, strategyScore
                ))
                .latestPrice(i.latestPrice())
                .stopLoss(atrRisk.stopLoss())
                .takeProfit(atrRisk.takeProfit())
                .atrAtSignal(atrRisk.atr())
                .atrPercent(atrRisk.atrPercent())
                .riskRewardRatio(atrRisk.riskRewardRatio())
                .candleRangeAtrMultiple(atrRisk.candleRangeAtrMultiple())
                .volatilityLevel(atrRisk.volatilityLevel())
                .atrOverextended(atrRisk.overextended())
                .atrExplanation(atrRisk.explanation())
                .explanation(explanation)
                .generatedAt(signalGeneratedAt)
                .build());
    }

    private String serializeDecisionPath(FinalDecisionResult finalDecision) {
        try {
            return objectMapper.writeValueAsString(finalDecision.adjustments());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize final decision path", exception);
        }
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
                indicator.getLatestVolume() != null
                        ? indicator.getLatestVolume()
                        : indicator.getVolumeSma20(),
                indicator.getVolumeSma20(),
                indicator.getRelativeVolume()
        );
    }

    private IndicatorSnapshot previousSnapshot(IndicatorSnapshot current) {
        return technicalIndicatorRepository
                .findTop100BySymbolAndIntervalCodeOrderByCandleOpenTimeDesc(
                        current.symbol(), current.intervalCode())
                .stream()
                .filter(candidate -> candidate.getCandleOpenTime().isBefore(current.candleOpenTime()))
                .findFirst()
                .map(this::toSnapshot)
                .orElse(null);
    }

    private MovingAverageBreakdown movingAverageScore(
            IndicatorSnapshot current,
            IndicatorSnapshot previous
    ) {
        int direction = scoreTrendDirection(current);
        int structure = scoreTrendStructure(current, previous);
        int strength = scoreTrendStrength(current);
        int priceLocation = scoreTrendPriceLocation(current);
        return new MovingAverageBreakdown(direction, structure, strength, priceLocation);
    }

    private int scoreTrendDirection(IndicatorSnapshot i) {
        int score = 0;
        if (i.ema20().compareTo(i.ema50()) > 0) {
            score += 4;
        }
        if (i.ema50().compareTo(i.ema200()) > 0) {
            score += 4;
        }
        return score;
    }

    private int scoreTrendStructure(IndicatorSnapshot current, IndicatorSnapshot previous) {
        int score = 0;
        boolean bullishAlignment = current.ema20().compareTo(current.ema50()) > 0
                && current.ema50().compareTo(current.ema200()) > 0;
        if (bullishAlignment) {
            score += 4;
        }
        if (previous != null) {
            if (current.ema20().compareTo(previous.ema20()) > 0) score += 1;
            if (current.ema50().compareTo(previous.ema50()) > 0) score += 1;
            if (current.ema200().compareTo(previous.ema200()) > 0) score += 1;
        }
        return score;
    }

    private int scoreTrendStrength(IndicatorSnapshot i) {
        BigDecimal separationPercent = percentDifference(i.ema20(), i.ema50());
        if (separationPercent.signum() <= 0) {
            return 0;
        }

        var thresholds = scoringProperties.trend();
        int separationScore;
        if (separationPercent.compareTo(thresholds.emaGapWeak()) < 0) separationScore = 1;
        else if (separationPercent.compareTo(thresholds.emaGapModerate()) < 0) separationScore = 2;
        else if (separationPercent.compareTo(thresholds.emaGapOptimal()) <= 0) separationScore = 3;
        else if (separationPercent.compareTo(thresholds.emaGapOverextended()) <= 0) separationScore = 2;
        else separationScore = 1;

        BigDecimal atrRatio = i.atr14().signum() == 0
                ? BigDecimal.ZERO
                : i.ema20().subtract(i.ema50()).abs()
                    .divide(i.atr14(), MC);
        int atrStrengthScore;
        if (atrRatio.compareTo(thresholds.emaSeparationAtrWeak()) < 0) atrStrengthScore = 0;
        else if (atrRatio.compareTo(thresholds.emaSeparationAtrModerate()) < 0) atrStrengthScore = 1;
        else if (atrRatio.compareTo(thresholds.emaSeparationAtrStrong()) <= 0) atrStrengthScore = 3;
        else atrStrengthScore = 2;

        return Math.min(6, separationScore + atrStrengthScore);
    }

    private int scoreTrendPriceLocation(IndicatorSnapshot i) {
        int score = 0;
        if (i.latestPrice().compareTo(i.ema200()) > 0) score += 2;
        if (i.latestPrice().compareTo(i.sma20()) > 0) score += 2;

        BigDecimal smaDistance = percentDifference(i.latestPrice(), i.sma20());
        if (smaDistance.compareTo(scoringProperties.trend().sma20Overextended()) > 0) {
            score = Math.max(0, score - 1);
        }
        return score;
    }

    private MomentumBreakdown momentumScore(IndicatorSnapshot i) {
        BigDecimal rsiValue = i.rsi14();
        var momentumThresholds = scoringProperties.momentum();
        int rsi;
        if (rsiValue.compareTo(momentumThresholds.rsiBullish()) >= 0
                && rsiValue.compareTo(momentumThresholds.rsiStrong()) <= 0) {
            rsi = 7;
        } else if (rsiValue.compareTo(momentumThresholds.rsiStrong()) > 0
                && rsiValue.compareTo(momentumThresholds.rsiHot()) <= 0) {
            rsi = 6;
        } else if (rsiValue.compareTo(momentumThresholds.rsiRecovering()) >= 0
                && rsiValue.compareTo(momentumThresholds.rsiBullish()) < 0) {
            rsi = 4;
        } else if (rsiValue.compareTo(momentumThresholds.rsiHot()) > 0
                && rsiValue.compareTo(momentumThresholds.rsiOverbought()) <= 0) {
            rsi = 3;
        } else if (rsiValue.compareTo(momentumThresholds.rsiWeak()) >= 0
                && rsiValue.compareTo(momentumThresholds.rsiRecovering()) < 0) {
            rsi = 2;
        } else if (rsiValue.compareTo(momentumThresholds.rsiOversold()) < 0) {
            rsi = 2;
        } else {
            rsi = 1;
        }

        boolean bullishCross = i.macd().compareTo(i.macdSignal()) > 0;
        boolean positiveHistogram = i.macdHistogram().signum() > 0;
        BigDecimal atrReference = i.atr14() == null
                ? BigDecimal.ZERO
                : i.atr14().abs();
        BigDecimal histogramAtrRatio = atrReference.signum() == 0
                ? BigDecimal.ZERO
                : i.macdHistogram().abs()
                    .divide(atrReference, 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

        int macd;
        if (bullishCross && positiveHistogram) {
            macd = histogramAtrRatio.compareTo(momentumThresholds.macdAtrStrong()) >= 0 ? 8
                    : histogramAtrRatio.compareTo(momentumThresholds.macdAtrModerate()) >= 0 ? 7
                    : histogramAtrRatio.compareTo(momentumThresholds.macdAtrWeak()) >= 0 ? 6 : 5;
        } else if (bullishCross) {
            macd = 4;
        } else if (positiveHistogram) {
            macd = 3;
        } else {
            macd = 0;
        }

        return new MomentumBreakdown(rsi, macd);
    }

    private BandsVolumeBreakdown bandsVolumeScore(IndicatorSnapshot i) {
        BigDecimal price = i.latestPrice();
        BigDecimal upper = i.bollingerUpper();
        BigDecimal lower = i.bollingerLower();
        BigDecimal width = upper.subtract(lower);
        BigDecimal percentB = width.signum() == 0
                ? BigDecimal.valueOf(0.5)
                : price.subtract(lower).divide(width, 8, RoundingMode.HALF_UP);

        int bollinger = percentB.compareTo(BigDecimal.ZERO) < 0 ? 0
                : percentB.compareTo(BigDecimal.valueOf(0.40)) < 0 ? 2
                : percentB.compareTo(BigDecimal.valueOf(0.55)) < 0 ? 4
                : percentB.compareTo(BigDecimal.valueOf(0.85)) <= 0 ? 6
                : percentB.compareTo(BigDecimal.ONE) <= 0 ? 4 : 2;

        BigDecimal rvol = i.relativeVolume();
        int relativeVolume = rvol.compareTo(BigDecimal.valueOf(2.0)) >= 0 ? 8
                : rvol.compareTo(BigDecimal.valueOf(1.5)) >= 0 ? 7
                : rvol.compareTo(BigDecimal.valueOf(1.2)) >= 0 ? 5
                : rvol.compareTo(BigDecimal.ONE) >= 0 ? 3
                : rvol.compareTo(BigDecimal.valueOf(0.75)) >= 0 ? 1 : 0;

        boolean priceConfirms = i.latestPrice().compareTo(i.sma20()) > 0;
        boolean momentumConfirms = i.macdHistogram().signum() >= 0;
        int volumeConfirmation;
        if (!priceConfirms) {
            volumeConfirmation = 0; // high volume on falling price is not bullish confirmation
        } else if (rvol.compareTo(BigDecimal.valueOf(1.5)) >= 0 && momentumConfirms) {
            volumeConfirmation = 6;
        } else if (rvol.compareTo(BigDecimal.valueOf(1.2)) >= 0 && momentumConfirms) {
            volumeConfirmation = 5;
        } else if (rvol.compareTo(BigDecimal.ONE) >= 0) {
            volumeConfirmation = momentumConfirms ? 4 : 2;
        } else {
            volumeConfirmation = 1;
        }

        return new BandsVolumeBreakdown(bollinger, relativeVolume, volumeConfirmation);
    }

    private BigDecimal percentDifference(BigDecimal value, BigDecimal reference) {
        if (value == null || reference == null || reference.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return value.subtract(reference)
                .divide(reference.abs(), 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private int positiveStrengthScore(BigDecimal percentage, int maximum, double... thresholds) {
        if (percentage == null || percentage.signum() <= 0) {
            return 0;
        }
        int score = 1;
        for (double threshold : thresholds) {
            if (percentage.compareTo(BigDecimal.valueOf(threshold)) >= 0) {
                score++;
            }
        }
        return Math.min(score, maximum);
    }

    private int sentimentScore(BigDecimal sentiment) {
        double normalized = Math.max(-1, Math.min(1, sentiment.doubleValue()));
        return (int) Math.round((normalized + 1) * 7.5);
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
        if (total >= 30) return SignalDecision.SELL;
        return SignalDecision.STRONG_SELL;
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

    private String serializeSentiment(List<ProviderSentiment> providers) {
        try {
            return objectMapper.writeValueAsString(providers);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private String serializeAnalysisBreakdown(
            IndicatorSnapshot i,
            MovingAverageBreakdown movingAverages,
            MomentumBreakdown momentum,
            BandsVolumeBreakdown bandsVolume,
            FundamentalScoreResult fundamentals,
            MarketRegimeAssessment regimeAssessment,
            StrategyProfile strategyProfile,
            StrategyScoreResult strategyScore
    ) {
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("marketRegime", Map.of(
                "value", regimeAssessment.regime().name(),
                "confidence", regimeAssessment.confidence(),
                "evidence", regimeAssessment.evidence(),
                "status", "This regime selected the active strategy profile"
        ));
        breakdown.put("dynamicStrategy", Map.of(
                "value", strategyProfile.strategy().name(),
                "version", strategyProfile.version(),
                "explanation", strategyProfile.explanation(),
                "entryAllowed", strategyProfile.entryAllowed(),
                "normalizedScore", strategyScore.normalizedScore(),
                "rawScore", strategyScore.rawScore(),
                "maximumScore", strategyScore.maximumScore()
        ));
        breakdown.put("trendGroups", Map.of(
                "direction", componentDetail(
                        "EMA20 > EMA50 and EMA50 > EMA200",
                        "Directional hierarchy", movingAverages.direction(), 8,
                        movingAverages.direction() == 8 ? "Both bullish direction checks confirmed"
                                : movingAverages.direction() > 0 ? "Partial bullish direction" : "No bullish direction confirmation"),
                "structure", componentDetail(
                        "Bullish EMA alignment plus EMA slopes",
                        "Alignment and persistence", movingAverages.structure(), 7,
                        movingAverages.structure() >= 6 ? "Aligned trend with rising averages"
                                : movingAverages.structure() >= 4 ? "Bullish alignment; slopes are mixed" : "Trend structure is incomplete"),
                "strength", componentDetail(
                        "EMA20/EMA50 separation",
                        "Percentage gap and gap relative to ATR", movingAverages.strength(), 6,
                        movingAverages.strength() >= 5 ? "Strong trend without excessive extension"
                                : movingAverages.strength() >= 3 ? "Moderate trend strength" : "Weak trend strength"),
                "priceLocation", componentDetail(
                        "Price above EMA200 and SMA20",
                        "Current price location", movingAverages.priceLocation(), 4,
                        movingAverages.priceLocation() == 4 ? "Price confirms both long- and short-term trend"
                                : movingAverages.priceLocation() > 0 ? "Partial price confirmation" : "Price does not confirm trend"),
                "note", "Correlated trend evidence is intentionally grouped; total remains 25"
        ));
        breakdown.put("emaCross", componentDetail(
                i.ema20() + " vs " + i.ema50(),
                formatPercent(percentDifference(i.ema20(), i.ema50())),
                movingAverages.direction(), 8,
                movingAverages.direction() >= 6 ? "Strong bullish separation"
                        : movingAverages.direction() >= 3 ? "Moderate bullish separation"
                        : movingAverages.direction() > 0 ? "Weak bullish separation" : "EMA20 is not above EMA50"
        ));
        breakdown.put("priceEma200", componentDetail(
                i.latestPrice() + " vs " + i.ema200(),
                formatPercent(percentDifference(i.latestPrice(), i.ema200())),
                movingAverages.priceLocation(), 4,
                movingAverages.priceLocation() >= 3 ? "Price is clearly above the long-term trend"
                        : movingAverages.priceLocation() > 0 ? "Price is slightly above EMA200" : "Price is below EMA200"
        ));
        breakdown.put("emaAlignment", componentDetail(
                "EMA20 / EMA50 / EMA200",
                "20=" + i.ema20() + ", 50=" + i.ema50() + ", 200=" + i.ema200(),
                movingAverages.structure(), 7,
                movingAverages.structure() >= 6 ? "Strong bullish EMA alignment"
                        : movingAverages.structure() > 0 ? "Bullish alignment with limited spacing" : "EMAs are not bullishly aligned"
        ));
        breakdown.put("sma20", componentDetail(
                i.latestPrice() + " vs " + i.sma20(),
                formatPercent(percentDifference(i.latestPrice(), i.sma20())),
                movingAverages.priceLocation(), 4,
                movingAverages.priceLocation() >= 3 ? "Price confirms the short-term trend"
                        : movingAverages.priceLocation() > 0 ? "Weak SMA20 confirmation" : "Price is below SMA20"
        ));
        breakdown.put("rsi", componentDetail(
                i.rsi14().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                rsiStatus(i.rsi14()), momentum.rsi(), 7, rsiStatus(i.rsi14())
        ));
        breakdown.put("macd", componentDetail(
                "MACD=" + i.macd() + ", signal=" + i.macdSignal(),
                "Histogram=" + i.macdHistogram(), momentum.macd(), 8,
                macdStatus(i)
        ));
        breakdown.put("bollinger", componentDetail(
                "Price=" + i.latestPrice(),
                "Lower=" + i.bollingerLower() + ", middle=" + i.bollingerMiddle() + ", upper=" + i.bollingerUpper(),
                bandsVolume.bollinger(), 6, bollingerStatus(i)
        ));
        breakdown.put("relativeVolume", componentDetail(
                i.relativeVolume().setScale(2, RoundingMode.HALF_UP) + "x",
                "Current volume versus 20-period average",
                bandsVolume.relativeVolume(), 8,
                i.relativeVolume().compareTo(BigDecimal.valueOf(1.2)) >= 0 ? "Volume confirms interest" : "Volume is weak"
        ));
        breakdown.put("volumeSma20", componentDetail(
                i.latestVolume().setScale(4, RoundingMode.HALF_UP) + " / avg "
                        + i.volumeSma20().setScale(4, RoundingMode.HALF_UP),
                "Directional confirmation using price, MACD and volume baseline",
                bandsVolume.volumeSma20(), 6,
                bandsVolume.volumeSma20() >= 5 ? "Volume strongly confirms the bullish move"
                        : bandsVolume.volumeSma20() >= 3 ? "Volume moderately confirms price direction"
                        : "Volume does not confirm a bullish move"
        ));
        breakdown.put("fundamentals", fundamentalDetails(fundamentals));
        try {
            return objectMapper.writeValueAsString(breakdown);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }


    private String serializeMarketContext(MarketContextSnapshot context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize market context", exception);
        }
    }

    private String serializeStrategyBreakdown(
            MarketRegimeAssessment regime,
            StrategyProfile profile,
            StrategyScoreResult score,
            int baseTrend,
            int baseVolume,
            int baseMomentum,
            int baseSentiment,
            int baseFundamentals,
            int baseRaw,
            int baseMaximum
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("regime", regime.regime().name());
        result.put("regimeConfidence", regime.confidence());
        result.put("regimeEvidence", regime.evidence());
        result.put("strategy", profile.strategy().name());
        result.put("strategyVersion", profile.version());
        result.put("entryAllowed", profile.entryAllowed());
        result.put("explanation", profile.explanation());
        result.put("base", Map.of(
                "trend", baseTrend,
                "volume", baseVolume,
                "momentum", baseMomentum,
                "sentiment", baseSentiment,
                "fundamentals", baseFundamentals,
                "raw", baseRaw,
                "maximum", baseMaximum
        ));
        Map<String, Object> active = new LinkedHashMap<>();
        active.put("trend", Map.of("score", score.trendScore(), "maximum", profile.trendMaximum()));
        active.put("volume", Map.of("score", score.volumeScore(), "maximum", profile.volumeMaximum()));
        active.put("momentum", Map.of("score", score.momentumScore(), "maximum", profile.momentumMaximum()));
        active.put("sentiment", Map.of("score", score.sentimentScore(), "maximum", profile.sentimentMaximum()));
        active.put("fundamentals", Map.of("score", score.fundamentalScore(), "maximum", profile.fundamentalMaximum()));
        active.put("raw", score.rawScore());
        active.put("maximum", score.maximumScore());
        active.put("normalized", score.normalizedScore());
        active.put("decision", score.decision().name());
        result.put("active", active);
        result.put("thresholds", Map.of(
                "strongBuy", profile.strongBuyThreshold(),
                "buy", profile.buyThreshold(),
                "watch", profile.watchThreshold(),
                "neutral", profile.neutralThreshold(),
                "sell", profile.sellThreshold()
        ));
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }


    private Map<String, Object> fundamentalDetails(FundamentalScoreResult fundamentals) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", fundamentals.total());
        result.put("maximum", fundamentals.maximum());
        result.put("riskLevel", fundamentals.riskLevel());
        result.put("components", fundamentals.components().stream()
                .map(this::fundamentalComponentDetail)
                .toList());
        result.put("finalScoreLabel", "Final Fundamental Score");
        result.put("ownership", fundamentalOwnershipDetail(fundamentals.ownership()));
        return result;
    }

    private Map<String, Object> fundamentalOwnershipDetail(
            com.crypto.dto.FundamentalOwnershipDetails ownership
    ) {
        if (ownership == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("circulatingSupply", ownership.circulatingSupply());
        result.put("referenceSupply", ownership.referenceSupply());
        result.put("nonCirculatingSupply", ownership.nonCirculatingSupply());
        result.put("teamSupply", ownership.teamSupply());
        result.put("treasurySupply", ownership.treasurySupply());
        result.put("privateInvestorSupply", ownership.privateInvestorSupply());
        result.put("lockedSupply", ownership.lockedSupply());
        result.put("knownCompanyControlledSupply", ownership.knownCompanyControlledSupply());
        result.put("publicCirculatingRatio", ownership.publicCirculatingRatio());
        result.put("knownCompanyControlledRatio", ownership.knownCompanyControlledRatio());
        result.put("referenceLabel", ownership.referenceLabel());
        result.put("status", ownership.status());
        return result;
    }

    private Map<String, Object> fundamentalComponentDetail(FundamentalComponentScore component) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", component.code());
        result.put("label", component.label());
        result.put("score", component.score());
        result.put("maximum", component.maximum());
        result.put("value", component.value());
        result.put("metric", component.metric());
        result.put("status", component.status());
        return result;
    }
    private Map<String, Object> componentDetail(String value, String metric, int score, int maximum, String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("value", value);
        result.put("metric", metric);
        result.put("score", score);
        result.put("maximum", maximum);
        result.put("status", status);
        return result;
    }

    private String formatPercent(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private String rsiStatus(BigDecimal rsi) {
        if (rsi.compareTo(BigDecimal.valueOf(80)) > 0) return "Extremely overbought";
        if (rsi.compareTo(BigDecimal.valueOf(70)) > 0) return "Overbought";
        if (rsi.compareTo(BigDecimal.valueOf(50)) >= 0) return "Healthy bullish momentum";
        if (rsi.compareTo(BigDecimal.valueOf(40)) >= 0) return "Neutral to weak momentum";
        if (rsi.compareTo(BigDecimal.valueOf(30)) >= 0) return "Bearish momentum";
        return "Oversold";
    }

    private String macdStatus(IndicatorSnapshot i) {
        if (i.macd().compareTo(i.macdSignal()) > 0 && i.macdHistogram().signum() > 0) return "Bullish MACD confirmation";
        if (i.macd().compareTo(i.macdSignal()) > 0) return "MACD above signal, histogram not confirmed";
        if (i.macdHistogram().signum() > 0) return "Histogram improving without crossover";
        return "Bearish MACD condition";
    }

    private String bollingerStatus(IndicatorSnapshot i) {
        if (i.latestPrice().compareTo(i.bollingerUpper()) >= 0) return "Price at or above upper band; possibly overextended";
        if (i.latestPrice().compareTo(i.bollingerMiddle()) >= 0) return "Price above middle band";
        if (i.latestPrice().compareTo(i.bollingerLower()) >= 0) return "Price below middle band";
        return "Price below lower band";
    }


    private record MovingAverageBreakdown(int direction, int structure, int strength, int priceLocation) {
        int total() { return direction + structure + strength + priceLocation; }
    }

    private record MomentumBreakdown(int rsi, int macd) {
        int total() { return rsi + macd; }
    }

    private record BandsVolumeBreakdown(int bollinger, int relativeVolume, int volumeSma20) {
        int total() { return bollinger + relativeVolume + volumeSma20; }
    }
}
