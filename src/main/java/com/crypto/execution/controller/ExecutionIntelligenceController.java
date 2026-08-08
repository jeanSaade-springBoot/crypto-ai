package com.crypto.execution.controller;

import com.crypto.execution.domain.ExecutionOpportunity;
import com.crypto.execution.repository.ExecutionOpportunityRepository;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import com.crypto.wallet.repository.WalletTradeRepository;
import com.crypto.domain.SignalDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;

@RestController
@RequestMapping("/api/execution-intelligence")
@RequiredArgsConstructor
public class ExecutionIntelligenceController {

    private final ExecutionOpportunityRepository repository;
    private final TradeSignalRepository tradeSignalRepository;
    private final WalletTradeRepository walletTradeRepository;
    private final WalletManagedPositionRepository walletManagedPositionRepository;

    @GetMapping("/opportunities")
    public List<OpportunityView> opportunities() {
        return repository.findTop50ByOrderByUpdatedAtDesc().stream()
                .map(OpportunityView::from)
                .toList();
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        ZoneId zone = ZoneId.of("Asia/Riyadh");
        Instant startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant();

        long signalsToday = tradeSignalRepository.countByGeneratedAtGreaterThanEqual(startOfDay);
        long coinsScanned = tradeSignalRepository.countDistinctSymbolsSince(startOfDay);
        long buyCandidates = tradeSignalRepository.countBuyCandidatesSince(
                startOfDay, SignalDecision.BUY, SignalDecision.STRONG_BUY);
        long consolidated = repository.countByExecutionSourceInAndUpdatedAtGreaterThanEqual(
                List.of("CONSOLIDATED_BUY", "ACCUMULATED_EVIDENCE", "DEFERRED_CONTINUATION",
                        "SCOUT_ENTRY", "CONFIRMATION_ADD", "TREND_ADD"), startOfDay);
        long opportunitiesFound = repository.countByStartedAtGreaterThanEqual(startOfDay);

        List<ExecutionOpportunity> activeOpportunities = repository.findTop50ByStatusInOrderByUpdatedAtDesc(
                List.of("BUILDING", "WEAKENING", "BLOCKED", "CONFIRMED"));
        long buildingNow = activeOpportunities.stream()
                .filter(o -> "BUILDING".equalsIgnoreCase(o.getStatus()))
                .count();
        long recoveringNow = activeOpportunities.stream()
                .filter(o -> "WEAKENING".equalsIgnoreCase(o.getStatus()) && o.getHealthMomentum() > 0)
                .count();
        long weakeningNow = activeOpportunities.stream()
                .filter(o -> "WEAKENING".equalsIgnoreCase(o.getStatus()) && o.getHealthMomentum() <= 0)
                .count();
        long blockedNow = activeOpportunities.stream()
                .filter(o -> "BLOCKED".equalsIgnoreCase(o.getStatus()))
                .count();
        long readyNow = activeOpportunities.stream()
                .filter(o -> "CONFIRMED".equalsIgnoreCase(o.getStatus()))
                .count();

        // AI Performance is position-based, not BUY-ledger-row based.
        // Progressive position building can create multiple BUY wallet rows for one position,
        // so counting BUY rows would inflate Executed after scout/confirmation/trend additions.
        long executed = walletManagedPositionRepository.findAll().stream()
                .filter(position -> position.getOpenedAt() != null && !position.getOpenedAt().isBefore(startOfDay))
                .count();
        long activePositions = walletManagedPositionRepository.countByStatus("OPEN");

        // Financial outcomes come directly from the executed wallet SELL ledger.
        // Calculate in Java so the dashboard does not depend on database-specific aggregate typing.
        var closedLedger = walletTradeRepository.findClosedTradesBetween(startOfDay, Instant.now());
        long closedTrades = closedLedger.size();
        long wins = closedLedger.stream()
                .filter(trade -> trade.getRealizedPnlUsdt() != null && trade.getRealizedPnlUsdt().signum() > 0)
                .count();
        long losses = closedLedger.stream()
                .filter(trade -> trade.getRealizedPnlUsdt() != null && trade.getRealizedPnlUsdt().signum() < 0)
                .count();
        long breakeven = closedLedger.stream()
                .filter(trade -> trade.getRealizedPnlUsdt() != null && trade.getRealizedPnlUsdt().signum() == 0)
                .count();
        long profitableClosed = wins;

        BigDecimal realizedPnl = closedLedger.stream()
                .map(trade -> trade.getRealizedPnlUsdt() == null ? BigDecimal.ZERO : trade.getRealizedPnlUsdt())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossProfit = closedLedger.stream()
                .map(trade -> trade.getRealizedPnlUsdt() == null ? BigDecimal.ZERO : trade.getRealizedPnlUsdt())
                .filter(value -> value.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossLoss = closedLedger.stream()
                .map(trade -> trade.getRealizedPnlUsdt() == null ? BigDecimal.ZERO : trade.getRealizedPnlUsdt())
                .filter(value -> value.signum() < 0)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profitFactor = grossLoss.signum() == 0
                ? (grossProfit.signum() > 0 ? null : BigDecimal.ZERO)
                : grossProfit.divide(grossLoss, 4, java.math.RoundingMode.HALF_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("signalsToday", signalsToday);
        result.put("coinsScanned", coinsScanned);
        result.put("buyCandidates", buyCandidates);
        result.put("consolidated", consolidated);
        result.put("opportunitiesFound", opportunitiesFound);
        result.put("buildingNow", buildingNow);
        result.put("recoveringNow", recoveringNow);
        result.put("weakeningNow", weakeningNow);
        result.put("blockedNow", blockedNow);
        result.put("readyNow", readyNow);
        result.put("executed", executed);
        result.put("activePositions", activePositions);
        result.put("closedProfitably", profitableClosed);
        result.put("closedTrades", closedTrades);
        result.put("wins", wins);
        result.put("losses", losses);
        result.put("breakeven", breakeven);
        result.put("realizedPnlUsdt", realizedPnl);
        long decided = wins + losses;
        result.put("winRatePercent", decided == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(wins).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(decided), 2, java.math.RoundingMode.HALF_UP));
        result.put("profitFactor", profitFactor);
        result.put("updatedAt", Instant.now());
        return result;
    }

    private Number number(Object[] values, int index) {
        if (values == null || values.length <= index || values[index] == null) return 0L;
        return (Number) values[index];
    }

    private BigDecimal decimal(Object[] values, int index) {
        if (values == null || values.length <= index || values[index] == null) return BigDecimal.ZERO;
        Object value = values[index];
        if (value instanceof BigDecimal decimal) return decimal;
        return new BigDecimal(value.toString());
    }

    @GetMapping("/opportunities/active")
    public List<OpportunityView> activeOpportunities() {
        return repository.findTop50ByStatusInOrderByUpdatedAtDesc(
                        List.of("BUILDING", "WEAKENING", "BLOCKED", "CONFIRMED"))
                .stream()
                .map(OpportunityView::from)
                .toList();
    }

    public record OpportunityView(
            Long id,
            String symbol,
            String direction,
            String status,
            Instant startedAt,
            Instant lastEvidenceAt,
            Long latestSignalId,
            int evidenceCount,
            int buyCount,
            int watchCount,
            int neutralCount,
            int bearishCount,
            int evidenceScore,
            int opportunityHealth,
            int healthMomentum,
            int evidenceMomentum,
            Instant lastBearishAt,
            int averageSignalScore,
            int averageConfidence,
            String fiveMinuteDecision,
            String oneHourDecision,
            String executionSource,
            int recommendedPositionPercent,
            String decisionCode,
            String decisionExplanation,
            Instant executedAt
    ) {
        static OpportunityView from(ExecutionOpportunity value) {
            return new OpportunityView(
                    value.getId(),
                    value.getSymbol(),
                    value.getDirection(),
                    value.getStatus(),
                    value.getStartedAt(),
                    value.getLastEvidenceAt(),
                    value.getLatestSignal() == null ? null : value.getLatestSignal().getId(),
                    value.getEvidenceCount(),
                    value.getBuyCount(),
                    value.getWatchCount(),
                    value.getNeutralCount(),
                    value.getBearishCount(),
                    value.getEvidenceScore(),
                    value.getOpportunityHealth(),
                    value.getHealthMomentum(),
                    value.getEvidenceMomentum(),
                    value.getLastBearishAt(),
                    value.getAverageSignalScore(),
                    value.getAverageConfidence(),
                    value.getFiveMinuteDecision(),
                    value.getOneHourDecision(),
                    value.getExecutionSource(),
                    value.getRecommendedPositionPercent(),
                    value.getDecisionCode(),
                    value.getDecisionExplanation(),
                    value.getExecutedAt()
            );
        }
    }
}
