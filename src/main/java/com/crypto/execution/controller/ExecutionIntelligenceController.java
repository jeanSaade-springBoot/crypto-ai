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
import org.springframework.web.bind.annotation.RequestParam;
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
    public Map<String, Object> summary(@RequestParam(defaultValue = "ALL_TIME") String period) {
        ZoneId zone = ZoneId.of("Asia/Riyadh");
        Instant now = Instant.now();
        Instant from = periodStart(period, zone, now);

        long coinsScanned = "ALL_TIME".equalsIgnoreCase(period)
                ? tradeSignalRepository.countDistinctSymbols()
                : tradeSignalRepository.countDistinctSymbolsSince(from);
        long opportunitiesFound = "ALL_TIME".equalsIgnoreCase(period)
                ? repository.count()
                : repository.countByStartedAtGreaterThanEqual(from);

        // Live state: these values intentionally describe what the execution engine is doing RIGHT NOW.
        List<ExecutionOpportunity> liveOpportunities = repository.findTop50ByStatusInOrderByUpdatedAtDesc(
                List.of("BUILDING", "WEAKENING", "BLOCKED", "CONFIRMED"));
        long buildingNow = liveOpportunities.stream()
                .filter(o -> "BUILDING".equalsIgnoreCase(o.getStatus()))
                .count();
        long recoveringNow = liveOpportunities.stream()
                .filter(o -> "WEAKENING".equalsIgnoreCase(o.getStatus()) && o.getHealthMomentum() > 0)
                .count();
        long weakeningNow = liveOpportunities.stream()
                .filter(o -> "WEAKENING".equalsIgnoreCase(o.getStatus()) && o.getHealthMomentum() <= 0)
                .count();
        long blockedNow = liveOpportunities.stream()
                .filter(o -> "BLOCKED".equalsIgnoreCase(o.getStatus()))
                .count();
        long readyNow = liveOpportunities.stream()
                .filter(o -> "CONFIRMED".equalsIgnoreCase(o.getStatus()))
                .count();

        // Period activity: terminal opportunities that were blocked/rejected during the selected window.
        // CANCELLED is included because BEARISH_REVERSAL and OPPORTUNITY_HEALTH_EXHAUSTED are
        // legitimate rejected opportunities, even though they are no longer in live BLOCKED state.
        List<String> rejectedStatuses = List.of("BLOCKED", "CANCELLED");
        long blockedRejected = "ALL_TIME".equalsIgnoreCase(period)
                ? repository.countByStatusIn(rejectedStatuses)
                : repository.countByStatusInAndUpdatedAtGreaterThanEqual(rejectedStatuses, from);

        long executed = "ALL_TIME".equalsIgnoreCase(period)
                ? walletManagedPositionRepository.count()
                : walletManagedPositionRepository.countByOpenedAtGreaterThanEqual(from);
        long activePositions = walletManagedPositionRepository.countByStatus("OPEN");

        // Financial truth is the executed SELL ledger.
        var closedLedger = walletTradeRepository.findClosedTradesBetween(from, now.plusMillis(1));
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
        result.put("period", normalizePeriod(period));
        result.put("periodFrom", from);
        result.put("coinsScanned", coinsScanned);
        result.put("opportunitiesFound", opportunitiesFound);
        result.put("liveOpportunities", liveOpportunities.size());
        result.put("buildingNow", buildingNow);
        result.put("recoveringNow", recoveringNow);
        result.put("weakeningNow", weakeningNow);
        result.put("blockedNow", blockedNow);
        result.put("readyNow", readyNow);
        result.put("blockedRejected", blockedRejected);
        result.put("executed", executed);
        result.put("activePositions", activePositions);
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
        result.put("updatedAt", now);
        return result;
    }

    private Instant periodStart(String period, ZoneId zone, Instant now) {
        return switch (normalizePeriod(period)) {
            case "TODAY" -> LocalDate.now(zone).atStartOfDay(zone).toInstant();
            case "LAST_24_HOURS" -> now.minus(java.time.Duration.ofHours(24));
            case "LAST_7_DAYS" -> now.minus(java.time.Duration.ofDays(7));
            case "LAST_30_DAYS" -> now.minus(java.time.Duration.ofDays(30));
            default -> Instant.EPOCH;
        };
    }

    private String normalizePeriod(String period) {
        if (period == null) return "ALL_TIME";
        return switch (period.trim().toUpperCase()) {
            case "TODAY" -> "TODAY";
            case "LAST_24_HOURS", "24H" -> "LAST_24_HOURS";
            case "LAST_7_DAYS", "7D" -> "LAST_7_DAYS";
            case "LAST_30_DAYS", "30D" -> "LAST_30_DAYS";
            default -> "ALL_TIME";
        };
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
