package com.crypto.position.service;

import com.crypto.domain.*;
import com.crypto.position.domain.PositionAnalysis;
import com.crypto.position.domain.PositionRecommendation;
import com.crypto.position.dto.PositionAnalysisView;
import com.crypto.position.repository.PositionAnalysisRepository;
import com.crypto.wallet.domain.WalletManagedPosition;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Read-only advisory engine for positions already owned by the automatic wallet.
 * It NEVER executes a trade and NEVER modifies AnalysisService/FinalDecisionService output.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PositionManagementService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final WalletManagedPositionRepository managedPositionRepository;
    private final PositionAnalysisRepository analysisRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Optional<PositionAnalysis> analyze(TradeSignal signal) {
        if (signal == null || signal.getId() == null || signal.getSymbol() == null
                || signal.getLatestPrice() == null || signal.getLatestPrice().signum() <= 0) {
            return Optional.empty();
        }

        String symbol = signal.getSymbol().trim().toUpperCase(Locale.ROOT);
        WalletManagedPosition position = managedPositionRepository
                .findTopBySymbolAndStatusOrderByOpenedAtDesc(symbol, "OPEN")
                .orElse(null);
        if (position == null || position.getId() == null || position.getQuantity() == null
                || position.getQuantity().signum() <= 0) {
            return Optional.empty();
        }

        if (analysisRepository.existsByWalletPositionIdAndTradeSignalId(position.getId(), signal.getId())) {
            return analysisRepository.findTopByWalletPositionIdOrderByAnalyzedAtDesc(position.getId());
        }

        BigDecimal entry = nvl(position.getAverageEntryPriceUsdt());
        BigDecimal price = signal.getLatestPrice();
        BigDecimal pnl = price.subtract(entry).multiply(position.getQuantity());
        BigDecimal pnlPercent = entry.signum() == 0
                ? ZERO
                : price.subtract(entry).divide(entry, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        long holdingMinutes = Math.max(0, Duration.between(position.getOpenedAt(), Instant.now()).toMinutes());

        int trendDeterioration = trendDeterioration(signal);
        int momentumExhaustion = momentumExhaustion(signal);
        int profitProtection = profitProtection(signal, pnlPercent);
        int riskEvents = riskEvents(signal);
        int opportunityCost = opportunityCost(signal);
        int exitScore = Math.min(25, trendDeterioration + momentumExhaustion
                + profitProtection + riskEvents + opportunityCost);

        PositionRecommendation recommendation = exitScore >= 17
                ? PositionRecommendation.EXIT
                : exitScore >= 10 ? PositionRecommendation.REDUCE : PositionRecommendation.HOLD;
        int confidence = confidence(exitScore, recommendation);
        List<String> evidence = evidence(signal, pnlPercent, trendDeterioration,
                momentumExhaustion, profitProtection, riskEvents, opportunityCost);
        String explanation = recommendation + " advisory: " + String.join("; ", evidence);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("advisoryOnly", true);
        details.put("currentMarketDecision", signal.getDecision());
        details.put("originalMarketDecision", signal.getOriginalDecision());
        details.put("trendScore", signal.getTrendScore());
        details.put("trendStructureScore", signal.getTrendStructureScore());
        details.put("momentumScore", signal.getMomentumScore());
        details.put("volumeScore", signal.getVolumeScore());
        details.put("marketRegime", signal.getMarketRegime());
        details.put("liquidityStatus", signal.getLiquidityStatus());
        details.put("btcContextStatus", signal.getBtcContextStatus());
        details.put("derivativesStatus", signal.getDerivativesStatus());
        details.put("scores", Map.of(
                "trendDeterioration", trendDeterioration,
                "momentumExhaustion", momentumExhaustion,
                "profitProtection", profitProtection,
                "riskEvents", riskEvents,
                "opportunityCost", opportunityCost,
                "exitScore", exitScore,
                "maximum", 25
        ));
        details.put("evidence", evidence);

        PositionAnalysis saved = analysisRepository.save(PositionAnalysis.builder()
                .walletPosition(position)
                .tradeSignal(signal)
                .symbol(symbol)
                .intervalCode(signal.getInterval())
                .entryPriceUsdt(entry)
                .currentPriceUsdt(price)
                .unrealizedPnlUsdt(pnl)
                .unrealizedPnlPercent(pnlPercent)
                .holdingMinutes(holdingMinutes)
                .trendDeteriorationScore(trendDeterioration)
                .momentumExhaustionScore(momentumExhaustion)
                .profitProtectionScore(profitProtection)
                .riskEventScore(riskEvents)
                .opportunityCostScore(opportunityCost)
                .exitScore(exitScore)
                .recommendation(recommendation)
                .confidence(confidence)
                .explanation(limit(explanation, 2000))
                .detailsJson(toJson(details))
                .advisoryOnly(true)
                .analyzedAt(Instant.now())
                .build());

        log.info("Position advisory created: position={}, signal={}, symbol={}, recommendation={}, exitScore={}/25",
                position.getId(), signal.getId(), symbol, recommendation, exitScore);
        return Optional.of(saved);
    }

    @Transactional(readOnly = true)
    public List<PositionAnalysisView> latest() {
        return analysisRepository.findTop100ByOrderByAnalyzedAtDesc().stream().map(this::view).toList();
    }

    private int trendDeterioration(TradeSignal s) {
        int score = 0;
        if (s.getTrendScore() <= 5) score += 5;
        else if (s.getTrendScore() <= 10) score += 4;
        else if (s.getTrendScore() <= 15) score += 2;
        if (s.getTrendStructureScore() <= 1) score += 2;
        else if (s.getTrendStructureScore() <= 3) score += 1;
        if (s.getDecision() == SignalDecision.STRONG_SELL) score += 1;
        return Math.min(8, score);
    }

    private int momentumExhaustion(TradeSignal s) {
        int score = s.getMomentumScore() <= 3 ? 4 : s.getMomentumScore() <= 7 ? 3
                : s.getMomentumScore() <= 10 ? 1 : 0;
        if (s.getMacdScore() == 0) score++;
        return Math.min(5, score);
    }

    private int profitProtection(TradeSignal s, BigDecimal pnlPercent) {
        int score = 0;
        if (pnlPercent.compareTo(BigDecimal.valueOf(3)) >= 0) score += 2;
        else if (pnlPercent.compareTo(BigDecimal.ONE) >= 0) score += 1;
        if (pnlPercent.signum() > 0 && s.getTrendScore() <= 10) score += 2;
        if (pnlPercent.signum() > 0 && (s.getDecision() == SignalDecision.SELL
                || s.getDecision() == SignalDecision.STRONG_SELL)) score += 1;
        return Math.min(5, score);
    }

    private int riskEvents(TradeSignal s) {
        int score = 0;
        if (s.getBtcContextStatus() == BtcContextStatus.CONFLICT) score++;
        if (s.getLiquidityStatus() == LiquidityContextStatus.STOP_EXPOSED) score += 2;
        if (!s.isDerivativesEntryAllowed()) score++;
        return Math.min(4, score);
    }

    private int opportunityCost(TradeSignal s) {
        if (s.getConfidenceScore() < 45) return 2;
        if (s.getConfidenceScore() < 60) return 1;
        return 0;
    }

    private int confidence(int score, PositionRecommendation recommendation) {
        int distance = switch (recommendation) {
            case HOLD -> 10 - score;
            case REDUCE -> Math.min(score - 9, 17 - score);
            case EXIT -> score - 16;
        };
        return Math.max(50, Math.min(95, 55 + Math.max(0, distance) * 7));
    }

    private List<String> evidence(TradeSignal s, BigDecimal pnlPct, int trend, int momentum,
                                  int protection, int risk, int opportunity) {
        List<String> e = new ArrayList<>();
        e.add("Unrealized P/L is " + pnlPct.setScale(2, RoundingMode.HALF_UP) + "%");
        e.add("Trend deterioration pressure " + trend + "/8 (trend " + s.getTrendScore()
                + "/25, structure " + s.getTrendStructureScore() + "/7)");
        e.add("Momentum exhaustion pressure " + momentum + "/5 (momentum "
                + s.getMomentumScore() + "/15)");
        if (protection > 0) e.add("Profit-protection pressure " + protection + "/5");
        if (risk > 0) e.add("Contextual risk pressure " + risk + "/4");
        if (opportunity > 0) e.add("Low-conviction opportunity-cost pressure " + opportunity + "/3");
        if (trend <= 2 && momentum <= 1) e.add("Buy thesis remains broadly intact");
        return e;
    }

    private PositionAnalysisView view(PositionAnalysis a) {
        return new PositionAnalysisView(a.getId(), a.getWalletPosition().getId(),
                a.getTradeSignal().getId(), a.getSymbol(), a.getIntervalCode(),
                a.getEntryPriceUsdt(), a.getCurrentPriceUsdt(), a.getUnrealizedPnlUsdt(),
                a.getUnrealizedPnlPercent(), a.getHoldingMinutes(), a.getExitScore(),
                a.getRecommendation(), a.getConfidence(), a.getExplanation(),
                a.isAdvisoryOnly(), a.getAnalyzedAt());
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Could not serialize position analysis", ex); }
    }
    private BigDecimal nvl(BigDecimal value) { return value == null ? ZERO : value; }
    private String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
