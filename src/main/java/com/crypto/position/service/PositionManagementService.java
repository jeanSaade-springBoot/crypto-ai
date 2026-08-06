package com.crypto.position.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
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
 * Advisory-only manager for positions already owned by the automatic wallet.
 *
 * It compares the immutable BUY thesis stored on WalletManagedPosition with the
 * latest TradeSignal. It does not recalculate indicators, change the market
 * decision, modify the wallet, or execute an exit.
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
        if (!validSignal(signal)) return Optional.empty();

        String symbol = signal.getSymbol().trim().toUpperCase(Locale.ROOT);
        WalletManagedPosition position = managedPositionRepository
                .findTopBySymbolAndStatusOrderByOpenedAtDesc(symbol, "OPEN")
                .orElse(null);
        if (!validPosition(position)) return Optional.empty();

        if (analysisRepository.existsByWalletPositionIdAndTradeSignalId(position.getId(), signal.getId())) {
            return analysisRepository.findTopByWalletPositionIdOrderByAnalyzedAtDesc(position.getId());
        }

        BigDecimal entry = nvl(position.getAverageEntryPriceUsdt());
        BigDecimal price = signal.getLatestPrice();
        BigDecimal pnl = price.subtract(entry).multiply(position.getQuantity());
        BigDecimal pnlPercent = percentageChange(entry, price);
        long holdingMinutes = Math.max(0,
                Duration.between(position.getOpenedAt(), Instant.now()).toMinutes());

        PositionRecommendation hardRecommendation = hardRiskRecommendation(position, price);

        ThesisComparison thesis = compareThesis(position, signal);
        int trendDeterioration = thesis.trendPressure();
        int momentumExhaustion = thesis.momentumPressure();
        int profitProtection = profitProtection(pnlPercent, thesis);
        int riskEvents = stopRiskPressure(position, price);
        int opportunityCost = 0; // intentionally excluded from v1
        int exitScore = Math.min(25, trendDeterioration + momentumExhaustion
                + profitProtection + riskEvents);

        int authority = timeframeAuthority(signal.getInterval());
        PositionRecommendation recommendation = hardRecommendation != null
                ? hardRecommendation
                : recommendation(exitScore, profitProtection, pnlPercent, authority);
        int confidence = confidence(exitScore, recommendation, hardRecommendation != null);

        List<String> evidence = evidence(position, signal, pnlPercent, thesis,
                trendDeterioration, momentumExhaustion, profitProtection,
                riskEvents, authority, hardRecommendation);
        String explanation = recommendation + " advisory: " + String.join("; ", evidence);

        Map<String, Object> details = details(position, signal, pnlPercent, thesis,
                trendDeterioration, momentumExhaustion, profitProtection,
                riskEvents, opportunityCost, exitScore, authority, evidence);

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
        return analysisRepository.findTop100ByOrderByAnalyzedAtDesc().stream()
                .map(this::view)
                .toList();
    }

    private boolean validSignal(TradeSignal signal) {
        return signal != null && signal.getId() != null && signal.getSymbol() != null
                && signal.getLatestPrice() != null && signal.getLatestPrice().signum() > 0;
    }

    private boolean validPosition(WalletManagedPosition position) {
        return position != null && position.getId() != null && position.getQuantity() != null
                && position.getQuantity().signum() > 0;
    }

    private PositionRecommendation hardRiskRecommendation(WalletManagedPosition p, BigDecimal price) {
        if (p.getStopLossUsdt() != null && p.getStopLossUsdt().signum() > 0
                && price.compareTo(p.getStopLossUsdt()) <= 0) {
            return PositionRecommendation.STOP_LOSS;
        }
        if (p.getTakeProfitUsdt() != null && p.getTakeProfitUsdt().signum() > 0
                && price.compareTo(p.getTakeProfitUsdt()) >= 0) {
            return PositionRecommendation.TAKE_PROFIT;
        }
        return null;
    }

    private ThesisComparison compareThesis(WalletManagedPosition p, TradeSignal current) {
        int entryTrend = valueOrCurrent(p.getEntryTrendScore(), current.getTrendScore());
        int entryStructure = valueOrCurrent(p.getEntryStructureScore(), current.getTrendStructureScore());
        int entryMomentum = valueOrCurrent(p.getEntryMomentumScore(), current.getMomentumScore());
        int entryVolume = valueOrCurrent(p.getEntryVolumeScore(), current.getVolumeScore());
        int entryConfidence = valueOrCurrent(p.getEntryConfidence(), current.getConfidenceScore());
        int entryTotal = valueOrCurrent(p.getEntryTotalScore(), current.getTotalScore());

        int trendDrop = positiveDrop(entryTrend, current.getTrendScore());
        int structureDrop = positiveDrop(entryStructure, current.getTrendStructureScore());
        int momentumDrop = positiveDrop(entryMomentum, current.getMomentumScore());
        int volumeDrop = positiveDrop(entryVolume, current.getVolumeScore());
        int confidenceDrop = positiveDrop(entryConfidence, current.getConfidenceScore());
        int totalDrop = positiveDrop(entryTotal, current.getTotalScore());

        int trendPressure = Math.min(8,
                points(trendDrop, 3, 6, 10, 3)
                        + points(structureDrop, 2, 3, 5, 2)
                        + (current.getTrendScore() <= 8 ? 2 : current.getTrendScore() <= 13 ? 1 : 0));

        int momentumPressure = Math.min(5,
                points(momentumDrop, 3, 5, 8, 2)
                        + (current.getMomentumScore() <= 4 ? 2 : current.getMomentumScore() <= 8 ? 1 : 0));

        return new ThesisComparison(entryTrend, current.getTrendScore(), trendDrop,
                entryStructure, current.getTrendStructureScore(), structureDrop,
                entryMomentum, current.getMomentumScore(), momentumDrop,
                entryVolume, current.getVolumeScore(), volumeDrop,
                entryConfidence, current.getConfidenceScore(), confidenceDrop,
                entryTotal, current.getTotalScore(), totalDrop,
                trendPressure, momentumPressure);
    }

    private int profitProtection(BigDecimal pnlPercent, ThesisComparison thesis) {
        if (pnlPercent.signum() <= 0) return 0;
        int score = 0;
        if (pnlPercent.compareTo(BigDecimal.valueOf(5)) >= 0) score += 2;
        else if (pnlPercent.compareTo(BigDecimal.valueOf(2)) >= 0) score += 1;
        if (thesis.trendDrop() >= 6 || thesis.structureDrop() >= 3) score += 2;
        if (thesis.momentumDrop() >= 5 || thesis.totalDrop() >= 15) score += 1;
        return Math.min(5, score);
    }

    private int stopRiskPressure(WalletManagedPosition p, BigDecimal price) {
        BigDecimal stop = p.getStopLossUsdt();
        BigDecimal entry = p.getAverageEntryPriceUsdt();
        if (stop == null || stop.signum() <= 0 || entry == null || entry.compareTo(stop) <= 0) return 0;
        BigDecimal totalRoom = entry.subtract(stop);
        BigDecimal remaining = price.subtract(stop);
        if (remaining.signum() <= 0) return 4;
        BigDecimal ratio = remaining.divide(totalRoom, 8, RoundingMode.HALF_UP);
        if (ratio.compareTo(BigDecimal.valueOf(0.25)) <= 0) return 3;
        if (ratio.compareTo(BigDecimal.valueOf(0.50)) <= 0) return 1;
        return 0;
    }

    private PositionRecommendation recommendation(int exitScore, int profitProtection,
                                                  BigDecimal pnlPercent, int authority) {
        // A 1m advisory can warn or recommend reduction, but cannot independently
        // invalidate the full position thesis.
        if (authority >= 2 && exitScore >= 17) return PositionRecommendation.EXIT;
        if (authority >= 2 && pnlPercent.signum() > 0
                && profitProtection >= 4 && exitScore >= 10) {
            return PositionRecommendation.TAKE_PROFIT;
        }
        if (exitScore >= 10) return PositionRecommendation.REDUCE;
        return PositionRecommendation.HOLD;
    }

    private int timeframeAuthority(String interval) {
        if (interval == null) return 1;
        return switch (interval.toLowerCase(Locale.ROOT)) {
            case "1h", "4h", "1d" -> 3;
            case "5m", "15m", "30m" -> 2;
            default -> 1;
        };
    }

    private int confidence(int score, PositionRecommendation recommendation, boolean hardTrigger) {
        if (hardTrigger) return 95;
        int base = switch (recommendation) {
            case HOLD -> 85 - Math.min(30, score * 3);
            case REDUCE -> 65 + Math.min(20, Math.abs(score - 13) * 3);
            case TAKE_PROFIT -> 75 + Math.min(15, score);
            case STOP_LOSS -> 95;
            case EXIT -> 75 + Math.min(20, score - 16);
        };
        return Math.max(50, Math.min(95, base));
    }

    private List<String> evidence(WalletManagedPosition p, TradeSignal s, BigDecimal pnlPct,
                                  ThesisComparison t, int trend, int momentum, int protection,
                                  int risk, int authority, PositionRecommendation hard) {
        List<String> e = new ArrayList<>();
        e.add("Unrealized P/L is " + pnlPct.setScale(2, RoundingMode.HALF_UP) + "%");
        if (hard == PositionRecommendation.STOP_LOSS) {
            e.add("Current price reached the immutable position stop loss " + p.getStopLossUsdt());
            return e;
        }
        if (hard == PositionRecommendation.TAKE_PROFIT) {
            e.add("Current price reached the immutable position take-profit target " + p.getTakeProfitUsdt());
            return e;
        }
        e.add("Trend changed " + t.entryTrend() + "→" + t.currentTrend()
                + " and structure " + t.entryStructure() + "→" + t.currentStructure());
        e.add("Momentum changed " + t.entryMomentum() + "→" + t.currentMomentum()
                + " and volume " + t.entryVolume() + "→" + t.currentVolume());
        e.add("Total score changed " + t.entryTotal() + "→" + t.currentTotal()
                + "; confidence " + t.entryConfidence() + "→" + t.currentConfidence());
        e.add("Thesis pressure: trend " + trend + "/8, momentum " + momentum + "/5");
        if (protection > 0) e.add("Profit-protection pressure " + protection + "/5");
        if (risk > 0) e.add("Price is approaching the stored stop-loss; risk pressure " + risk + "/4");
        e.add("Timeframe authority " + authority + "/3 for " + s.getInterval());
        if (trend <= 2 && momentum <= 1) e.add("Original BUY thesis remains broadly intact");
        return e;
    }

    private Map<String, Object> details(WalletManagedPosition p, TradeSignal s,
                                        BigDecimal pnlPct, ThesisComparison t,
                                        int trend, int momentum, int protection,
                                        int risk, int opportunity, int exitScore,
                                        int authority, List<String> evidence) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("advisoryOnly", true);
        details.put("method", "IMMUTABLE_BUY_THESIS_COMPARISON");
        details.put("entrySignalId", p.getEntrySignalId());
        details.put("entryDecision", p.getEntryDecision());
        details.put("entryPriceUsdt", p.getAverageEntryPriceUsdt());
        details.put("stopLossUsdt", p.getStopLossUsdt());
        details.put("takeProfitUsdt", p.getTakeProfitUsdt());
        details.put("currentPriceUsdt", s.getLatestPrice());
        details.put("unrealizedPnlPercent", pnlPct);
        details.put("timeframeAuthority", authority);
        details.put("entry", Map.of(
                "trend", t.entryTrend(), "structure", t.entryStructure(),
                "momentum", t.entryMomentum(), "volume", t.entryVolume(),
                "confidence", t.entryConfidence(), "total", t.entryTotal()));
        details.put("current", Map.of(
                "trend", t.currentTrend(), "structure", t.currentStructure(),
                "momentum", t.currentMomentum(), "volume", t.currentVolume(),
                "confidence", t.currentConfidence(), "total", t.currentTotal(),
                "decision", String.valueOf(s.getDecision())));
        details.put("deltas", Map.of(
                "trendDrop", t.trendDrop(), "structureDrop", t.structureDrop(),
                "momentumDrop", t.momentumDrop(), "volumeDrop", t.volumeDrop(),
                "confidenceDrop", t.confidenceDrop(), "totalDrop", t.totalDrop()));
        details.put("scores", Map.of(
                "trendDeterioration", trend,
                "momentumExhaustion", momentum,
                "profitProtection", protection,
                "hardRisk", risk,
                "opportunityCost", opportunity,
                "exitScore", exitScore,
                "maximum", 25));
        details.put("evidence", evidence);
        return details;
    }

    private int points(int drop, int low, int medium, int high, int max) {
        if (drop >= high) return max;
        if (drop >= medium) return Math.max(1, max - 1);
        if (drop >= low) return 1;
        return 0;
    }

    private int valueOrCurrent(Integer value, int current) {
        return value == null ? current : value;
    }

    private int positiveDrop(int entry, int current) {
        return Math.max(0, entry - current);
    }

    private BigDecimal percentageChange(BigDecimal entry, BigDecimal current) {
        if (entry == null || entry.signum() == 0) return ZERO;
        return current.subtract(entry)
                .divide(entry, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
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
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize position analysis", ex);
        }
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record ThesisComparison(
            int entryTrend, int currentTrend, int trendDrop,
            int entryStructure, int currentStructure, int structureDrop,
            int entryMomentum, int currentMomentum, int momentumDrop,
            int entryVolume, int currentVolume, int volumeDrop,
            int entryConfidence, int currentConfidence, int confidenceDrop,
            int entryTotal, int currentTotal, int totalDrop,
            int trendPressure, int momentumPressure) {
    }
}
