package com.crypto.inspector.service;

import com.crypto.audit.domain.ProductionExitAudit;
import com.crypto.audit.repository.ProductionExitAuditRepository;
import com.crypto.domain.Candle;
import com.crypto.domain.PaperPosition;
import com.crypto.domain.TradeSignal;
import com.crypto.dto.TradeInspectorResponse;
import com.crypto.dto.TradeInspectorSummary;
import com.crypto.dto.TradeInspectorTradeView;
import com.crypto.repository.CandleRepository;
import com.crypto.repository.PaperPositionRepository;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.execution.domain.ExecutionOpportunity;
import com.crypto.execution.repository.ExecutionOpportunityRepository;
import com.crypto.position.domain.PositionAnalysis;
import com.crypto.position.domain.PositionManagementEvent;
import com.crypto.position.repository.PositionAnalysisRepository;
import com.crypto.position.repository.PositionManagementEventRepository;
import com.crypto.wallet.domain.WalletTrade;
import com.crypto.wallet.domain.WalletManagedPosition;
import com.crypto.wallet.repository.WalletTradeRepository;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

@Service
public class TradeInspectorService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final WalletTradeRepository walletTradeRepository;
    private final CandleRepository candleRepository;
    private final PaperPositionRepository paperPositionRepository;
    private final WalletManagedPositionRepository walletManagedPositionRepository;
    private final TradeSignalRepository tradeSignalRepository;
    private final ExecutionOpportunityRepository executionOpportunityRepository;
    private final ProductionExitAuditRepository productionExitAuditRepository;
    private final PositionAnalysisRepository positionAnalysisRepository;
    private final PositionManagementEventRepository positionManagementEventRepository;

    public TradeInspectorService(WalletTradeRepository walletTradeRepository,
                                 CandleRepository candleRepository,
                                 PaperPositionRepository paperPositionRepository,
                                 WalletManagedPositionRepository walletManagedPositionRepository,
                                 TradeSignalRepository tradeSignalRepository,
                                 ExecutionOpportunityRepository executionOpportunityRepository,
                                 ProductionExitAuditRepository productionExitAuditRepository,
                                 PositionAnalysisRepository positionAnalysisRepository,
                                 PositionManagementEventRepository positionManagementEventRepository) {
        this.walletTradeRepository = walletTradeRepository;
        this.candleRepository = candleRepository;
        this.paperPositionRepository = paperPositionRepository;
        this.walletManagedPositionRepository = walletManagedPositionRepository;
        this.tradeSignalRepository = tradeSignalRepository;
        this.executionOpportunityRepository = executionOpportunityRepository;
        this.productionExitAuditRepository = productionExitAuditRepository;
        this.positionAnalysisRepository = positionAnalysisRepository;
        this.positionManagementEventRepository = positionManagementEventRepository;
    }

    @Transactional(readOnly = true)
    public TradeInspectorResponse inspect(String requestedSymbol, String requestedVenue, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        String symbol = normalizeSymbol(requestedSymbol);
        String venue = requestedVenue == null ? "ALL" : requestedVenue.trim().toUpperCase(Locale.ROOT);

        // FIX-032: current persisted inspector rows come from wallet_trade and are therefore WALLET.
        // BINANCE is intentionally an empty prepared filter until the LIVE_MICRO execution bridge
        // writes real executions; never mislabel shadow trades as real Binance fills.
        if ("BINANCE".equals(venue)) {
            return new TradeInspectorResponse(summary(List.of()), List.of(), List.of());
        }
        if (!venue.equals("ALL") && !venue.equals("WALLET")) {
            throw new IllegalArgumentException("Trade Inspector venue must be ALL, WALLET or BINANCE.");
        }

        List<WalletTrade> closed = walletTradeRepository.findRecentClosedTrades(PageRequest.of(0, 100));
        if (symbol != null) {
            closed = closed.stream().filter(t -> symbol.equalsIgnoreCase(t.getSymbol())).toList();
        }
        List<WalletTrade> selectedSells = closed.stream().limit(limit).toList();

        List<WalletTrade> ledger = walletTradeRepository.findTop100ByOrderByExecutedAtDesc();
        List<TradeInspectorTradeView> views = new ArrayList<>();
        for (WalletTrade sell : selectedSells) {
            WalletTrade buy = findEntryTrade(sell, ledger);
            if (buy != null) {
                views.add(toView(buy, sell));
            }
        }

        List<String> symbols = closed.stream().map(WalletTrade::getSymbol).filter(Objects::nonNull)
                .distinct().sorted().toList();
        if (symbols.isEmpty()) {
            symbols = ledger.stream().map(WalletTrade::getSymbol).filter(Objects::nonNull).distinct().sorted().toList();
        }
        return new TradeInspectorResponse(summary(views), views, symbols);
    }


    /**
     * FIX-039: returns persisted BUY/STRONG_BUY candidates blocked by final entry authority.
     * The default window is the last three hours; caller-supplied timestamps are UTC instants.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> blockedBuys(String requestedSymbol, Instant requestedFrom, Instant requestedTo, int requestedLimit) {
        TimeWindow window = blockedSignalWindow(requestedFrom, requestedTo);
        int limit = Math.max(1, Math.min(requestedLimit, 250));
        String symbol = normalizeSymbol(requestedSymbol);
        return tradeSignalRepository.findBlockedBuys(
                        com.crypto.domain.SignalDecision.BUY,
                        com.crypto.domain.SignalDecision.STRONG_BUY,
                        symbol, window.from(), window.to(), PageRequest.of(0, limit))
                .stream().map(this::blockedBuyView).toList();
    }

    /**
     * FIX-039: a blocked SELL is an isolated/base SELL or STRONG_SELL whose persisted final
     * decision became a non-SELL state. This exposes the existing audit evidence only.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> blockedSells(String requestedSymbol, Instant requestedFrom, Instant requestedTo, int requestedLimit) {
        TimeWindow window = blockedSignalWindow(requestedFrom, requestedTo);
        int limit = Math.max(1, Math.min(requestedLimit, 250));
        String symbol = normalizeSymbol(requestedSymbol);
        return tradeSignalRepository.findBlockedSells(
                        com.crypto.domain.SignalDecision.SELL,
                        com.crypto.domain.SignalDecision.STRONG_SELL,
                        symbol, window.from(), window.to(), PageRequest.of(0, limit))
                .stream().map(this::blockedSellView).toList();
    }

    private TimeWindow blockedSignalWindow(Instant requestedFrom, Instant requestedTo) {
        Instant to = requestedTo == null ? Instant.now() : requestedTo;
        Instant from = requestedFrom == null ? to.minus(Duration.ofHours(3)) : requestedFrom;
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Blocked-signal From time must be before To time.");
        }
        return new TimeWindow(from, to);
    }

    /**
     * FIX-038: separate production-exit audit feed. The table intentionally does not expose
     * a generic signal "decision" column because that was misleading for TP/SL/mechanical exits.
     * Instead it exposes the real close trigger plus the source signal id/recommendation as context.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> productionExits(String requestedSymbol, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        String symbol = normalizeSymbol(requestedSymbol);
        List<ProductionExitAudit> rows = productionExitAuditRepository
                .findAllByOrderByAuditedAtDesc(PageRequest.of(0, Math.min(300, limit * 5)));
        if (symbol != null) {
            rows = rows.stream().filter(a -> symbol.equalsIgnoreCase(a.getSymbol())).toList();
        }
        return rows.stream().limit(limit).map(this::productionExitTableView).toList();
    }

    private Map<String, Object> blockedBuyView(TradeSignal signal) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("signalId", signal.getId());
        m.put("symbol", signal.getSymbol());
        m.put("interval", signal.getInterval());
        m.put("generatedAt", signal.getGeneratedAt());
        m.put("price", signal.getLatestPrice());
        m.put("decision", signal.getDecision() == null ? null : signal.getDecision().name());
        m.put("score", signal.getTotalScore());
        m.put("confidence", signal.getConfidenceScore());
        m.put("strategy", signal.getSelectedStrategy() == null ? null : signal.getSelectedStrategy().name());
        m.put("regime", signal.getMarketRegime() == null ? null : signal.getMarketRegime().name());
        m.put("blocker", primaryBlocker(signal));
        m.put("blockerExplanation", primaryBlockerExplanation(signal));
        m.put("finalExplanation", signal.getFinalDecisionExplanation());
        m.put("decisionPath", signal.getDecisionPath());
        return m;
    }

    private Map<String, Object> blockedSellView(TradeSignal signal) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("signalId", signal.getId());
        m.put("symbol", signal.getSymbol());
        m.put("interval", signal.getInterval());
        m.put("generatedAt", signal.getGeneratedAt());
        m.put("price", signal.getLatestPrice());
        m.put("originalDecision", signal.getOriginalDecision() == null ? null : signal.getOriginalDecision().name());
        m.put("decision", signal.getDecision() == null ? null : signal.getDecision().name());
        m.put("score", signal.getTotalScore());
        m.put("confidence", signal.getConfidenceScore());
        m.put("strategy", signal.getSelectedStrategy() == null ? null : signal.getSelectedStrategy().name());
        m.put("regime", signal.getMarketRegime() == null ? null : signal.getMarketRegime().name());
        m.put("blocker", "FINAL DECISION: " +
                (signal.getOriginalDecision() == null ? "SELL" : signal.getOriginalDecision().name()) + " → " +
                (signal.getDecision() == null ? "UNKNOWN" : signal.getDecision().name()));
        m.put("blockerExplanation", safeText(signal.getFinalDecisionExplanation(), signal.getExplanation()));
        m.put("decisionPath", signal.getDecisionPath());
        return m;
    }

    private String primaryBlocker(TradeSignal s) {
        if (!s.isStrategyEntryAllowed()) return "STRATEGY";
        if (!s.isAtrImmediateEntryAllowed()) return "ATR / ENTRY TIMING";
        if (!s.isConfluenceEntryAllowed()) return "MULTI-TIMEFRAME";
        if (!s.isBtcContextEntryAllowed()) return "BTC CONTEXT";
        if (!s.isDerivativesEntryAllowed()) return "DERIVATIVES";
        if (!s.isLiquidityEntryAllowed()) return "ORDER BOOK / LIQUIDITY";
        return "FINAL DECISION";
    }

    private String primaryBlockerExplanation(TradeSignal s) {
        if (!s.isStrategyEntryAllowed()) return safeText(s.getStrategyExplanation(), s.getFinalDecisionExplanation());
        if (!s.isAtrImmediateEntryAllowed()) return safeText(s.getAtrExplanation(), s.getFinalDecisionExplanation());
        if (!s.isConfluenceEntryAllowed()) return safeText(s.getConfluenceExplanation(), s.getFinalDecisionExplanation());
        if (!s.isBtcContextEntryAllowed()) return safeText(s.getBtcContextExplanation(), s.getFinalDecisionExplanation());
        if (!s.isDerivativesEntryAllowed()) return safeText(s.getDerivativesExplanation(), s.getFinalDecisionExplanation());
        if (!s.isLiquidityEntryAllowed()) return safeText(s.getLiquidityExplanation(), s.getFinalDecisionExplanation());
        return safeText(s.getFinalDecisionExplanation(), "Entry was blocked by final decision authority.");
    }

    private String safeText(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private Map<String, Object> productionExitTableView(ProductionExitAudit a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("auditId", a.getId());
        m.put("paperPositionId", a.getPaperPositionId());
        m.put("walletPositionId", a.getWalletPositionId());
        m.put("symbol", a.getSymbol());
        m.put("closeTrigger", a.getCloseTrigger());
        m.put("sourceSignalId", a.getSourceSignalId());
        m.put("positionAnalysisId", a.getPositionAnalysisId());
        m.put("positionRecommendation", a.getPositionRecommendation());
        m.put("entryPrice", a.getEntryPriceUsdt());
        m.put("exitPrice", a.getExitPriceUsdt());
        m.put("stopLoss", a.getStopLossUsdt());
        m.put("takeProfit", a.getTakeProfitUsdt());
        m.put("closeExplanation", a.getCloseExplanation());
        m.put("auditedAt", a.getAuditedAt());
        return m;
    }

    private WalletTrade findEntryTrade(WalletTrade sell, List<WalletTrade> ledger) {
        return ledger.stream()
                .filter(t -> t.getExecutedAt() != null && t.getExecutedAt().isBefore(sell.getExecutedAt()))
                .filter(t -> "EXECUTED".equalsIgnoreCase(t.getStatus()))
                .filter(t -> "BUY".equalsIgnoreCase(t.getSide()))
                .filter(t -> sell.getSymbol().equalsIgnoreCase(t.getSymbol()))
                .filter(t -> quantitiesMatch(t.getQuantity(), sell.getQuantity()))
                .max(Comparator.comparing(WalletTrade::getExecutedAt))
                .orElseGet(() -> ledger.stream()
                        .filter(t -> t.getExecutedAt() != null && t.getExecutedAt().isBefore(sell.getExecutedAt()))
                        .filter(t -> "EXECUTED".equalsIgnoreCase(t.getStatus()))
                        .filter(t -> "BUY".equalsIgnoreCase(t.getSide()))
                        .filter(t -> sell.getSymbol().equalsIgnoreCase(t.getSymbol()))
                        .max(Comparator.comparing(WalletTrade::getExecutedAt))
                        .orElse(null));
    }

    private boolean quantitiesMatch(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return false;
        BigDecimal max = a.abs().max(b.abs());
        if (max.signum() == 0) return true;
        BigDecimal relative = a.subtract(b).abs().divide(max, 8, RoundingMode.HALF_UP);
        return relative.compareTo(BigDecimal.valueOf(0.001)) <= 0;
    }

    private TradeInspectorTradeView toView(WalletTrade buy, WalletTrade sell) {
        Instant openedAt = buy.getExecutedAt();
        Instant closedAt = sell.getExecutedAt();
        List<Candle> candles = candleRepository.findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeAsc(
                sell.getSymbol(), "1m", openedAt.minusSeconds(60), closedAt.plus(Duration.ofMinutes(65)));

        List<Candle> during = candles.stream()
                .filter(c -> !c.getOpenTime().isBefore(openedAt.minusSeconds(60)))
                .filter(c -> !c.getOpenTime().isAfter(closedAt))
                .toList();

        BigDecimal bestPrice = during.stream().map(Candle::getHighPrice).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(sell.getPriceUsdt());
        BigDecimal worstPrice = during.stream().map(Candle::getLowPrice).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(sell.getPriceUsdt());

        BigDecimal entryPrice = buy.getPriceUsdt();
        BigDecimal exitPrice = sell.getPriceUsdt();
        BigDecimal realizedPercent = sell.getRealizedPnlPercent() != null
                ? sell.getRealizedPnlPercent()
                : percentChange(entryPrice, exitPrice);

        BigDecimal p15 = priceAt(candles, closedAt.plus(Duration.ofMinutes(15)));
        BigDecimal p30 = priceAt(candles, closedAt.plus(Duration.ofMinutes(30)));
        BigDecimal p60 = priceAt(candles, closedAt.plus(Duration.ofMinutes(60)));
        ExitAssessment assessment = assessExit(exitPrice, p15, p30, p60, realizedPercent);

        TradeSignal entry = buy.getSignal();
        TradeSignal exit = sell.getSignal();
        // FIX-028: recover the production position itself so the Inspector displays the
        // true terminal trigger. Legacy wallet rows may say SIGNAL_SELL even when the
        // position was actually closed by TAKE_PROFIT using a WATCH signal as context.
        PaperPosition paper = findPaperPosition(buy, sell);
        Long tradeHistoryId = paper == null ? null : paper.getId();

        WalletManagedPosition managed = entry == null ? null : walletManagedPositionRepository
                .findTopByEntrySignalIdOrderByOpenedAtDesc(entry.getId()).orElse(null);

        return new TradeInspectorTradeView(
                buy.getId(), sell.getId(), tradeHistoryId, "WALLET", sell.getSymbol(), openedAt, closedAt,
                Math.max(0, Duration.between(openedAt, closedAt).toMinutes()),
                sell.getQuantity(), entryPrice, exitPrice, sell.getRealizedPnlUsdt(), realizedPercent,
                entry == null ? null : entry.getId(),
                entry == null || entry.getDecision() == null ? "BUY" : entry.getDecision().name(),
                entry == null ? 0 : entry.getTotalScore(),
                entry == null ? 0 : entry.getConfidenceScore(),
                entry == null ? null : entry.getInterval(),
                entry == null || entry.getSelectedStrategy() == null ? null : entry.getSelectedStrategy().name(),
                entry == null || entry.getMarketRegime() == null ? null : entry.getMarketRegime().name(),
                managed != null && managed.getStopLossUsdt() != null ? managed.getStopLossUsdt() : entry == null ? null : entry.getStopLoss(),
                managed != null && managed.getTakeProfitUsdt() != null ? managed.getTakeProfitUsdt() : entry == null ? null : entry.getTakeProfit(),
                managed != null && managed.getProfitLockActivatedAt() != null,
                managed == null ? null : managed.getProfitLockPriceUsdt(),
                managed == null ? null : managed.getProfitLockProgressPercent(),
                managed == null ? null : managed.getProfitLockActivatedAt(),
                managed == null ? null : managed.getHighestPriceUsdt(),
                exit == null ? null : exit.getId(),
                exit == null || exit.getDecision() == null ? sell.getExecutionReason() : exit.getDecision().name(),
                exit == null ? null : exit.getTotalScore(),
                exit == null ? null : exit.getConfidenceScore(),
                paper != null && paper.getCloseReason() != null
                        ? paper.getCloseReason().replace('_', ' ')
                        : humanCloseReason(sell),
                bestPrice, percentChange(entryPrice, bestPrice),
                worstPrice, percentChange(entryPrice, worstPrice),
                p15, p30, p60, assessment.quality(), assessment.explanation()
        );
    }

    /**
     * FIX-028: locate the production PaperPosition for both signal-linked and mechanical
     * exits. Signal-pair lookup handles historical rows like BTC #145; the timestamp
     * fallback covers exits that have no exit_signal_id at all.
     */
    private PaperPosition findPaperPosition(WalletTrade buy, WalletTrade sell) {
        TradeSignal entry = buy == null ? null : buy.getSignal();
        TradeSignal exit = sell == null ? null : sell.getSignal();
        if (entry != null && exit != null) {
            PaperPosition paired = paperPositionRepository.findBySignalPair(entry.getId(), exit.getId()).stream()
                    .findFirst().orElse(null);
            if (paired != null) return paired;
        }
        if (sell == null || sell.getSymbol() == null || sell.getExecutedAt() == null) return null;
        return paperPositionRepository.findTop20BySymbolOrderByOpenedAtDesc(sell.getSymbol()).stream()
                .filter(p -> p.getOpenedAt() != null && !p.getOpenedAt().isAfter(sell.getExecutedAt()))
                .filter(p -> p.getClosedAt() != null
                        && Duration.between(p.getClosedAt(), sell.getExecutedAt()).abs().compareTo(Duration.ofSeconds(5)) <= 0)
                .findFirst().orElse(null);
    }

    private Map<String, Object> positionAnalysisView(PositionAnalysis analysis) {
        if (analysis == null) return Map.of();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", analysis.getId());
        m.put("tradeSignalId", analysis.getTradeSignal() == null ? null : analysis.getTradeSignal().getId());
        m.put("recommendation", analysis.getRecommendation() == null ? null : analysis.getRecommendation().name());
        m.put("confidence", analysis.getConfidence());
        m.put("exitScore", analysis.getExitScore());
        m.put("explanation", analysis.getExplanation());
        m.put("analyzedAt", analysis.getAnalyzedAt());
        return m;
    }

    private Map<String, Object> exitAuditView(ProductionExitAudit audit, PaperPosition paper) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (audit != null) {
            m.put("id", audit.getId());
            m.put("paperPositionId", audit.getPaperPositionId());
            m.put("walletPositionId", audit.getWalletPositionId());
            m.put("closeTrigger", audit.getCloseTrigger());
            m.put("sourceSignalId", audit.getSourceSignalId());
            m.put("sourceSignalDecision", audit.getSourceSignalDecision());
            m.put("sourceSignalOriginalDecision", audit.getSourceSignalOriginalDecision());
            m.put("positionAnalysisId", audit.getPositionAnalysisId());
            m.put("positionRecommendation", audit.getPositionRecommendation());
            m.put("closeExplanation", audit.getCloseExplanation());
            m.put("auditedAt", audit.getAuditedAt());
            return m;
        }
        // Legacy fallback: expose the production position's real close reason even though
        // older wallet_trade rows cannot be rewritten safely in place.
        if (paper != null) {
            m.put("paperPositionId", paper.getId());
            m.put("closeTrigger", paper.getCloseReason());
            m.put("sourceSignalId", paper.getExitSignal() == null ? null : paper.getExitSignal().getId());
            m.put("sourceSignalDecision", paper.getExitSignal() == null || paper.getExitSignal().getDecision() == null
                    ? null : paper.getExitSignal().getDecision().name());
            m.put("closeExplanation", paper.getExitReason());
            m.put("legacyFallback", true);
        }
        return m;
    }

    private String humanCloseReason(WalletTrade sell) {
        String reason = sell.getExecutionReason();
        if (reason == null || reason.isBlank()) return "SELL";
        return switch (reason.toUpperCase(Locale.ROOT)) {
            case "POSITION_STOP_LOSS" -> "STOP LOSS";
            case "POSITION_TAKE_PROFIT" -> "TAKE PROFIT";
            case "POSITION_PROFIT_LOCK" -> "PROFIT LOCK";
            case "SIGNAL_SELL" -> "SELL SIGNAL";
            default -> reason.replace('_', ' ');
        };
    }

    private BigDecimal priceAt(List<Candle> candles, Instant target) {
        return candles.stream()
                .filter(c -> Duration.between(target, c.getOpenTime()).abs().compareTo(Duration.ofMinutes(3)) <= 0)
                .min(Comparator.comparing(c -> Duration.between(target, c.getOpenTime()).abs()))
                .map(Candle::getClosePrice).orElse(null);
    }

    private ExitAssessment assessExit(BigDecimal exitPrice, BigDecimal p15, BigDecimal p30, BigDecimal p60, BigDecimal realizedPercent) {
        List<BigDecimal> future = Stream.of(p15, p30, p60).filter(Objects::nonNull).toList();
        if (future.isEmpty() || exitPrice == null || exitPrice.signum() == 0) {
            return new ExitAssessment("PENDING", "Post-exit candles are not available yet.");
        }
        BigDecimal bestAfterExit = future.stream().max(Comparator.naturalOrder()).orElse(exitPrice);
        BigDecimal worstAfterExit = future.stream().min(Comparator.naturalOrder()).orElse(exitPrice);
        BigDecimal rebound = percentChange(exitPrice, bestAfterExit);
        BigDecimal continuedDown = percentChange(exitPrice, worstAfterExit);
        if (rebound != null && rebound.compareTo(BigDecimal.valueOf(0.50)) >= 0) {
            return new ExitAssessment("EARLY_EXIT", "Price rebounded " + formatPct(rebound) + " within 60 minutes after exit.");
        }
        if (continuedDown != null && continuedDown.compareTo(BigDecimal.valueOf(-0.50)) <= 0) {
            return new ExitAssessment("GOOD_EXIT", "Price continued " + formatPct(continuedDown) + " lower within 60 minutes after exit.");
        }
        if (realizedPercent != null && realizedPercent.signum() > 0) {
            return new ExitAssessment("GOOD_EXIT", "The trade realized a profit and no material post-exit rebound was detected.");
        }
        return new ExitAssessment("NEUTRAL_EXIT", "Price stayed within ±0.50% of the exit during the observed post-exit window.");
    }

    /**
     * FIX-030: read-only trade-level performance metrics for the one-look View Path.
     * Holding efficiency intentionally uses MFE capture rather than a subjective time target:
     * realized positive return / maximum favorable excursion, capped at 100%.
     */
    private Map<String, Object> tradePerformanceView(TradeInspectorTradeView trade) {
        if (trade == null) return Map.of();
        BigDecimal realized = nz(trade.realizedPnlPercent());
        BigDecimal mfe = nz(trade.maximumFavorablePercent());
        BigDecimal holdingEfficiency = BigDecimal.ZERO;
        if (realized.signum() > 0 && mfe.signum() > 0) {
            holdingEfficiency = realized.multiply(HUNDRED)
                    .divide(mfe, 2, RoundingMode.HALF_UP)
                    .min(HUNDRED).max(BigDecimal.ZERO);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("holdingMinutes", trade.holdingMinutes());
        m.put("mfePercent", trade.maximumFavorablePercent());
        m.put("maePercent", trade.maximumAdversePercent());
        m.put("holdingEfficiencyPercent", holdingEfficiency);
        m.put("exitQuality", trade.exitQuality());
        m.put("exitQualityExplanation", trade.exitQualityExplanation());
        return m;
    }

    /**
     * FIX-030: profit factor belongs to a sample of trades, not to one individual trade.
     * The path therefore shows the latest N completed trades as context while keeping the
     * individual node focused on its own holding time / MFE capture.
     */
    private Map<String, Object> recentPerformanceContext(int requestedWindow) {
        int window = Math.max(1, Math.min(requestedWindow, 100));
        List<WalletTrade> sells = walletTradeRepository.findRecentClosedTrades(PageRequest.of(0, window));
        List<WalletTrade> ledger = walletTradeRepository.findTop100ByOrderByExecutedAtDesc();
        List<TradeInspectorTradeView> recent = new ArrayList<>();
        for (WalletTrade sell : sells) {
            WalletTrade buy = findEntryTrade(sell, ledger);
            if (buy != null) recent.add(toView(buy, sell));
        }
        TradeInspectorSummary s = summary(recent);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("windowTrades", s.trades());
        m.put("profitFactor", s.profitFactor());
        m.put("winRate", s.winRate());
        m.put("wins", s.wins());
        m.put("losses", s.losses());
        m.put("netPnl", s.netPnl());
        return m;
    }

    private TradeInspectorSummary summary(List<TradeInspectorTradeView> trades) {
        int wins = (int) trades.stream().filter(t -> nz(t.realizedPnl()).signum() > 0).count();
        int losses = (int) trades.stream().filter(t -> nz(t.realizedPnl()).signum() < 0).count();
        BigDecimal net = trades.stream().map(t -> nz(t.realizedPnl())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossProfit = trades.stream().map(t -> nz(t.realizedPnl())).filter(v -> v.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossLoss = trades.stream().map(t -> nz(t.realizedPnl())).filter(v -> v.signum() < 0).map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = trades.isEmpty() ? BigDecimal.ZERO : net.divide(BigDecimal.valueOf(trades.size()), 8, RoundingMode.HALF_UP);
        BigDecimal avgWin = wins == 0 ? BigDecimal.ZERO : grossProfit.divide(BigDecimal.valueOf(wins), 8, RoundingMode.HALF_UP);
        BigDecimal avgLoss = losses == 0 ? BigDecimal.ZERO : grossLoss.negate().divide(BigDecimal.valueOf(losses), 8, RoundingMode.HALF_UP);
        BigDecimal winRate = trades.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(wins).multiply(HUNDRED).divide(BigDecimal.valueOf(trades.size()), 2, RoundingMode.HALF_UP);
        BigDecimal profitFactor = grossLoss.signum() == 0 ? (grossProfit.signum() > 0 ? BigDecimal.valueOf(999) : BigDecimal.ZERO) : grossProfit.divide(grossLoss, 4, RoundingMode.HALF_UP);
        return new TradeInspectorSummary(trades.size(), wins, losses, winRate, net, avg, avgWin, avgLoss, profitFactor);
    }



    /**
     * FIX-024 / Trade Inspector View Path:
     * Build a read-only, timestamped explanation of one executed BUY -> SELL lifecycle.
     * This deliberately reads the same persisted trade_signal / execution_opportunity / wallet
     * evidence that production created; it does not recompute, rescore, or mutate trading state.
     * KSA presentation is handled by the browser from the stored UTC/Binance timestamps.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> path(Long buyTradeId, Long sellTradeId) {
        WalletTrade buy = walletTradeRepository.findById(buyTradeId)
                .orElseThrow(() -> new IllegalArgumentException("BUY wallet trade was not found."));
        WalletTrade sell = walletTradeRepository.findById(sellTradeId)
                .orElseThrow(() -> new IllegalArgumentException("SELL wallet trade was not found."));
        if (!"BUY".equalsIgnoreCase(buy.getSide()) || !"SELL".equalsIgnoreCase(sell.getSide())) {
            throw new IllegalArgumentException("View Path requires a BUY wallet trade and a SELL wallet trade.");
        }
        if (!Objects.equals(normalizeSymbol(buy.getSymbol()), normalizeSymbol(sell.getSymbol()))) {
            throw new IllegalArgumentException("BUY and SELL must belong to the same symbol.");
        }

        TradeSignal entry = buy.getSignal();
        TradeSignal exit = sell.getSignal();
        PaperPosition paper = findPaperPosition(buy, sell);
        ProductionExitAudit exitAudit = paper == null ? null : productionExitAuditRepository
                .findTopByPaperPositionIdOrderByAuditedAtDesc(paper.getId()).orElse(null);
        Instant reference = buy.getExecutedAt();
        TradeSignal oneMinute = latestSignalAtOrBefore(buy.getSymbol(), "1m", reference);
        TradeSignal fiveMinute = latestSignalAtOrBefore(buy.getSymbol(), "5m", reference);
        TradeSignal oneHour = latestSignalAtOrBefore(buy.getSymbol(), "1h", reference);

        ExecutionOpportunity opportunity = entry == null ? null : executionOpportunityRepository
                .findTopByLatestSignalIdOrderByUpdatedAtDesc(entry.getId()).orElse(null);
        if (opportunity == null && buy.getExecutedAt() != null) {
            // FIX-024: progressive scout/add flows may update latest_signal_id after the first BUY.
            // Recover the BUY opportunity whose persisted lifecycle overlapped this exact wallet entry.
            opportunity = executionOpportunityRepository
                    .findTop10BySymbolAndStartedAtLessThanEqualAndUpdatedAtGreaterThanEqualOrderByUpdatedAtDesc(
                            buy.getSymbol(), buy.getExecutedAt(), buy.getExecutedAt())
                    .stream().filter(o -> "BUY".equalsIgnoreCase(o.getDirection())).findFirst().orElse(null);
        }
        WalletManagedPosition managed = entry == null ? null : walletManagedPositionRepository
                .findTopByEntrySignalIdOrderByOpenedAtDesc(entry.getId()).orElse(null);
        PositionAnalysis exitPositionAnalysis = managed == null || managed.getId() == null ? null
                : positionAnalysisRepository.findTopByWalletPositionIdOrderByAnalyzedAtDesc(managed.getId()).orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("symbol", buy.getSymbol());
        result.put("buyTradeId", buy.getId());
        result.put("sellTradeId", sell.getId());
        result.put("openedAt", buy.getExecutedAt());
        result.put("closedAt", sell.getExecutedAt());
        result.put("holdingSeconds", Math.max(0, Duration.between(buy.getExecutedAt(), sell.getExecutedAt()).toSeconds()));
        result.put("entryPrice", buy.getPriceUsdt());
        result.put("exitPrice", sell.getPriceUsdt());
        result.put("entryExecutionReason", buy.getExecutionReason());
        result.put("exitExecutionReason", sell.getExecutionReason());

        // FIX-028: actualExitTrigger is authoritative for the View Path. Prefer the new
        // immutable audit table, then the production paper_position close reason, then
        // fall back to the wallet ledger for pre-FIX-028 data. The linked TradeSignal is
        // displayed as context unless its persisted decision is genuinely SELL/STRONG_SELL.
        String actualExitTrigger = exitAudit != null && exitAudit.getCloseTrigger() != null
                ? exitAudit.getCloseTrigger()
                : paper != null && paper.getCloseReason() != null ? paper.getCloseReason() : sell.getExecutionReason();
        String actualExitExplanation = exitAudit != null && exitAudit.getCloseExplanation() != null
                ? exitAudit.getCloseExplanation()
                : paper != null ? paper.getExitReason() : sell.getExecutionMessage();
        boolean triggerIsSignalSell = actualExitTrigger != null
                && ("SELL".equalsIgnoreCase(actualExitTrigger)
                || "STRONG_SELL".equalsIgnoreCase(actualExitTrigger)
                || "SIGNAL_SELL".equalsIgnoreCase(actualExitTrigger));
        boolean sourceSignalIsSellTrigger = triggerIsSignalSell && exit != null && exit.getDecision() != null
                && ("SELL".equalsIgnoreCase(exit.getDecision().name())
                || "STRONG_SELL".equalsIgnoreCase(exit.getDecision().name()));
        result.put("actualExitTrigger", actualExitTrigger);
        result.put("actualExitExplanation", actualExitExplanation);
        result.put("exitSourceSignalRole", sourceSignalIsSellTrigger ? "SELL_TRIGGER" : "MARKET_CONTEXT_AT_EXIT");
        result.put("exitAudit", exitAuditView(exitAudit, paper));
        result.put("exitPositionAnalysis", positionAnalysisView(exitPositionAnalysis));
        result.put("realizedPnlPercent", sell.getRealizedPnlPercent());

        // FIX-030: Trade-path performance context is diagnostic only. Holding efficiency
        // measures how much of the favorable move was actually captured by the exit;
        // recent profit factor provides system context and is never used by execution.
        TradeInspectorTradeView completedTrade = toView(buy, sell);
        result.put("performance", tradePerformanceView(completedTrade));
        result.put("recentPerformance", recentPerformanceContext(20));

        result.put("opportunity", opportunityView(opportunity));
        result.put("oneMinute", signalPathView(oneMinute));
        result.put("fiveMinute", signalPathView(fiveMinute));
        result.put("oneHour", signalPathView(oneHour));
        result.put("entrySignal", signalPathView(entry));
        result.put("exitSignal", signalPathView(exit));
        result.put("decisionPath", entry == null ? null : entry.getDecisionPath());

        // FIX-025: FIX-024 explained why the BUY happened but jumped directly to the SELL.
        // Return the complete persisted position window so Trade Inspector can show how
        // the open trade evolved: confirmation adds, signal deterioration/recovery,
        // continuation context and the exact state present when the terminal SELL fired.
        List<WalletTrade> lifecycleTrades = walletTradeRepository
                .findBySymbolAndStatusAndExecutedAtBetweenOrderByExecutedAtAsc(
                        buy.getSymbol(), "EXECUTED", buy.getExecutedAt(), sell.getExecutedAt());
        result.put("walletLifecycle", lifecycleTrades.stream().map(this::walletLifecycleView).toList());

        // FIX-027: the one-look state map must be able to show the pre-entry transition
        // (for example ENA SELLING -> STABILIZING -> RECOVERING -> RECOVERY_PROBE), not
        // only what happened after the wallet BUY. Read a bounded 45-minute pre-entry
        // window; this is diagnostics only and never feeds production/replay decisions.
        Instant pathSignalStart = buy.getExecutedAt().minus(Duration.ofMinutes(45));
        List<TradeSignal> lifecycleSignals = tradeSignalRepository
                .findBySymbolAndGeneratedAtBetweenOrderByGeneratedAtAsc(
                        buy.getSymbol(), pathSignalStart, sell.getExecutedAt());
        result.put("signalLifecycle", lifecycleSignals.stream()
                .filter(this::isLifecycleSignal)
                .map(this::signalPathView)
                .toList());
        result.put("exitOneMinute", signalPathView(latestSignalAtOrBefore(buy.getSymbol(), "1m", sell.getExecutedAt())));
        result.put("exitFiveMinute", signalPathView(latestSignalAtOrBefore(buy.getSymbol(), "5m", sell.getExecutedAt())));
        result.put("exitOneHour", signalPathView(latestSignalAtOrBefore(buy.getSymbol(), "1h", sell.getExecutedAt())));

        // FIX-027: some important transition evidence occurs between scheduled signal
        // evaluations (ENA's ~900K-volume / ~88% taker-BUY expansion is the anchor).
        // Return a narrow read-only 1m candle band around entry so View Path can display
        // that real market phase without pretending a new TradeSignal existed.
        Instant evidenceFrom = buy.getExecutedAt().minus(Duration.ofMinutes(30));
        Instant evidenceTo = sell.getExecutedAt().isBefore(buy.getExecutedAt().plus(Duration.ofMinutes(30)))
                ? sell.getExecutedAt()
                : buy.getExecutedAt().plus(Duration.ofMinutes(30));
        result.put("entryEvidenceCandles", candleRepository
                .findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeAsc(
                        buy.getSymbol(), "1m", evidenceFrom, evidenceTo)
                .stream().filter(Candle::isClosed).map(this::candleEvidenceView).toList());

        Map<String, Object> management = new LinkedHashMap<>();
        if (managed != null) {
            management.put("stopLoss", managed.getStopLossUsdt());
            management.put("takeProfit", managed.getTakeProfitUsdt());
            management.put("highestPrice", managed.getHighestPriceUsdt());
            management.put("profitLockActivatedAt", managed.getProfitLockActivatedAt());
            management.put("profitLockPrice", managed.getProfitLockPriceUsdt());
            management.put("profitLockProgressPercent", managed.getProfitLockProgressPercent());
        }
        result.put("management", management);

        // FIX-066: expose immutable position-management changes in the same exact lifecycle
        // shown by Trade Inspector. This is read-only presentation data. In particular, a
        // TAKE_PROFIT_EXTENDED event must be visible as its own timeline phase with old TP,
        // new TP, market price, reason and KSA-rendered timestamp instead of being hidden in
        // the final managed-position snapshot.
        List<PositionManagementEvent> managementEvents = managed == null || managed.getId() == null
                ? List.of()
                : positionManagementEventRepository
                    .findByWalletPositionIdAndOccurredAtBetweenOrderByOccurredAtAsc(
                            managed.getId(), buy.getExecutedAt(), sell.getExecutedAt());
        result.put("managementEvents", managementEvents.stream().map(e -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("type", e.getEventType());
            item.put("oldValue", e.getOldValueUsdt());
            item.put("newValue", e.getNewValueUsdt());
            item.put("marketPrice", e.getMarketPriceUsdt());
            item.put("reason", e.getReason());
            item.put("occurredAt", e.getOccurredAt());
            return item;
        }).toList());
        return result;
    }

    private TradeSignal latestSignalAtOrBefore(String symbol, String interval, Instant reference) {
        if (symbol == null || interval == null || reference == null) return null;
        return tradeSignalRepository
                .findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(symbol, interval, reference)
                .orElse(null);
    }

    private Map<String, Object> opportunityView(ExecutionOpportunity o) {
        if (o == null) return Map.of();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId()); m.put("status", o.getStatus()); m.put("startedAt", o.getStartedAt());
        m.put("lastEvidenceAt", o.getLastEvidenceAt()); m.put("executedAt", o.getExecutedAt());
        m.put("evidenceCount", o.getEvidenceCount()); m.put("buyCount", o.getBuyCount());
        m.put("watchCount", o.getWatchCount()); m.put("neutralCount", o.getNeutralCount());
        m.put("bearishCount", o.getBearishCount()); m.put("evidenceScore", o.getEvidenceScore());
        m.put("health", o.getOpportunityHealth()); m.put("healthMomentum", o.getHealthMomentum());
        m.put("evidenceMomentum", o.getEvidenceMomentum()); m.put("averageScore", o.getAverageSignalScore());
        m.put("averageConfidence", o.getAverageConfidence()); m.put("fiveMinuteDecision", o.getFiveMinuteDecision());
        m.put("oneHourDecision", o.getOneHourDecision()); m.put("executionSource", o.getExecutionSource());
        m.put("recommendedPositionPercent", o.getRecommendedPositionPercent()); m.put("decisionCode", o.getDecisionCode());
        m.put("explanation", o.getDecisionExplanation());
        return m;
    }

    private Map<String, Object> signalPathView(TradeSignal s) {
        if (s == null) return Map.of();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId()); m.put("interval", s.getInterval()); m.put("generatedAt", s.getGeneratedAt());
        m.put("candleOpenTime", s.getCandleOpenTime()); m.put("price", s.getLatestPrice());
        m.put("originalDecision", s.getOriginalDecision() == null ? null : s.getOriginalDecision().name());
        m.put("decision", s.getDecision() == null ? null : s.getDecision().name());
        m.put("score", s.getTotalScore()); m.put("confidence", s.getConfidenceScore());
        m.put("trend", s.getTrendScore()); m.put("volume", s.getVolumeScore()); m.put("momentum", s.getMomentumScore());
        m.put("sentiment", s.getSentimentScore()); m.put("fundamental", s.getFundamentalScore());
        m.put("sentimentAvailable", s.isSentimentAvailable()); m.put("fundamentalAvailable", s.isFundamentalAvailable());
        m.put("emaCross", s.getEmaCrossScore()); m.put("priceEma200", s.getPriceEma200Score());
        m.put("emaAlignment", s.getEmaAlignmentScore()); m.put("sma20", s.getSma20Score());
        m.put("trendDirection", s.getTrendDirectionScore()); m.put("trendStructure", s.getTrendStructureScore());
        m.put("trendStrength", s.getTrendStrengthScore()); m.put("trendPriceLocation", s.getTrendPriceLocationScore());
        m.put("rsi", s.getRsiScore()); m.put("macd", s.getMacdScore()); m.put("bollinger", s.getBollingerScore());
        m.put("relativeVolume", s.getRelativeVolumeScore()); m.put("volumeSma20", s.getVolumeSma20Score());
        m.put("rawScore", s.getRawScore()); m.put("maximumAvailableScore", s.getMaximumAvailableScore());
        m.put("regime", s.getMarketRegime() == null ? null : s.getMarketRegime().name());
        m.put("regimeConfidence", s.getMarketRegimeConfidence());
        m.put("strategy", s.getSelectedStrategy() == null ? null : s.getSelectedStrategy().name());
        m.put("confluence", s.getConfluenceStatus() == null ? null : s.getConfluenceStatus().name());
        m.put("higherInterval", s.getConfluenceHigherInterval());
        m.put("higherDecision", s.getConfluenceHigherDecision() == null ? null : s.getConfluenceHigherDecision().name());
        m.put("higherTrend", s.getConfluenceHigherTrendScore());
        m.put("btcStatus", s.getBtcContextStatus() == null ? null : s.getBtcContextStatus().name());
        m.put("btcDecision", s.getBtcContextDecision() == null ? null : s.getBtcContextDecision().name());
        m.put("btcTrend", s.getBtcContextTrendScore()); m.put("btcCorrelation", s.getBtcCorrelation());
        m.put("btcBeta", s.getBtcBeta()); m.put("btcInfluence", s.getBtcInfluenceFactor());
        m.put("btcSampleSize", s.getBtcRelationshipSampleSize()); m.put("btcStable", s.isBtcRelationshipStable());
        m.put("btcExplanation", s.getBtcContextExplanation());
        m.put("liquidityStatus", s.getLiquidityStatus() == null ? null : s.getLiquidityStatus().name());
        m.put("liquidityEntryAllowed", s.isLiquidityEntryAllowed());
        m.put("orderBookImbalance", s.getOrderBookImbalance()); m.put("spreadPercent", s.getOrderBookSpreadPercent());
        m.put("bidDepth", s.getOrderBookBidDepth()); m.put("askDepth", s.getOrderBookAskDepth());
        m.put("bidWallPrice", s.getNearestBidWallPrice()); m.put("bidWallSize", s.getNearestBidWallSize());
        m.put("askWallPrice", s.getNearestAskWallPrice()); m.put("askWallSize", s.getNearestAskWallSize());
        m.put("targetBlocked", s.isOrderBookTargetBlocked()); m.put("stopExposed", s.isOrderBookStopExposed());
        m.put("orderBookObservations", s.getOrderBookObservations()); m.put("orderBookWindowSeconds", s.getOrderBookWindowSeconds());
        m.put("wallPersistenceSeconds", s.getOrderBookWallPersistenceSeconds()); m.put("orderBookVetoAllowed", s.isOrderBookVetoAllowed());
        m.put("orderBookInfluence", s.getOrderBookInfluenceFactor());
        m.put("liquidityExplanation", s.getLiquidityExplanation());
        m.put("derivativesStatus", s.getDerivativesStatus() == null ? null : s.getDerivativesStatus().name());
        m.put("fundingRate", s.getFundingRate()); m.put("fundingPercentile", s.getFundingPercentile());
        m.put("openInterest", s.getOpenInterest()); m.put("openInterestValue", s.getOpenInterestValue());
        m.put("openInterestChangePercent", s.getOpenInterestChangePercent()); m.put("derivativesPriceChangePercent", s.getDerivativesPriceChangePercent());
        m.put("derivativesConfidenceAdjustment", s.getDerivativesConfidenceAdjustment());
        m.put("atr", s.getAtrAtSignal()); m.put("atrPercent", s.getAtrPercent()); m.put("riskReward", s.getRiskRewardRatio());
        m.put("atrEntryType", s.getAtrEntryType()); m.put("atrOverextended", s.isAtrOverextended());
        m.put("atrImmediateEntryAllowed", s.isAtrImmediateEntryAllowed()); m.put("atrRecommendedPositionPercent", s.getAtrRecommendedPositionPercent());
        m.put("stopLoss", s.getStopLoss()); m.put("takeProfit", s.getTakeProfit());
        m.put("finalEntryAllowed", s.isFinalEntryAllowed()); m.put("finalExplanation", s.getFinalDecisionExplanation());

        // FIX-027: View Path is now a one-look sequential state map. Attach the exact
        // CLOSED candle that produced this persisted signal so the node can show the
        // market evidence the engine actually had at that step (volume, taker BUY
        // pressure and trade count) without recomputing strategy decisions in the UI.
        // This also protects Replay/Production interpretation: the diagnostic uses the
        // signal's own candle_open_time, never a later candle.
        if (s.getCandleOpenTime() != null && s.getInterval() != null) {
            candleRepository.findBySymbolAndIntervalCodeAndOpenTime(s.getSymbol(), s.getInterval(), s.getCandleOpenTime())
                    .ifPresent(c -> {
                        m.put("candleVolume", c.getVolume());
                        m.put("candleTrades", c.getNumberOfTrades());
                        m.put("candleClose", c.getClosePrice());
                        m.put("takerBuyBaseVolume", c.getTakerBuyBaseVolume());
                        BigDecimal takerBuyPercent = c.getVolume() == null || c.getVolume().signum() == 0 || c.getTakerBuyBaseVolume() == null
                                ? null
                                : c.getTakerBuyBaseVolume().multiply(HUNDRED).divide(c.getVolume(), 2, RoundingMode.HALF_UP);
                        m.put("takerBuyPercent", takerBuyPercent);
                    });
        }
        return m;
    }

    private boolean isLifecycleSignal(TradeSignal signal) {
        if (signal == null || signal.getDecision() == null) return false;
        // Keep the path readable: 1m carries timing while 5m/1h expose setup/authority
        // changes. We persist every non-NEUTRAL 1m state and every 5m/1h state so the
        // user can see exactly how confirmation strengthened or weakened before SELL.
        if ("5m".equalsIgnoreCase(signal.getInterval()) || "1h".equalsIgnoreCase(signal.getInterval())) return true;
        // FIX-027: keep raw bearish-to-neutral transitions because they are required to
        // explain STABILIZING/RECOVERING phases even when FinalDecisionService neutralized
        // the raw SELL. WATCH/BUY states remain visible as before.
        String finalDecision = signal.getDecision().name();
        String originalDecision = signal.getOriginalDecision() == null ? "" : signal.getOriginalDecision().name();
        return !"NEUTRAL".equalsIgnoreCase(finalDecision)
                || "SELL".equalsIgnoreCase(originalDecision)
                || "STRONG_SELL".equalsIgnoreCase(originalDecision);
    }

    private Map<String, Object> walletLifecycleView(WalletTrade trade) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", trade.getId());
        m.put("signalId", trade.getSignal() == null ? null : trade.getSignal().getId());
        m.put("side", trade.getSide());
        m.put("executedAt", trade.getExecutedAt());
        m.put("price", trade.getPriceUsdt());
        m.put("quantity", trade.getQuantity());
        m.put("grossAmount", trade.getGrossAmountUsdt());
        m.put("executionReason", trade.getExecutionReason());
        m.put("executionMessage", trade.getExecutionMessage());
        m.put("realizedPnlPercent", trade.getRealizedPnlPercent());
        return m;
    }

    private Map<String, Object> candleEvidenceView(Candle c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("openTime", c.getOpenTime());
        m.put("closeTime", c.getCloseTime());
        m.put("open", c.getOpenPrice());
        m.put("high", c.getHighPrice());
        m.put("low", c.getLowPrice());
        m.put("close", c.getClosePrice());
        m.put("volume", c.getVolume());
        m.put("trades", c.getNumberOfTrades());
        BigDecimal takerBuyPercent = c.getVolume() == null || c.getVolume().signum() == 0 || c.getTakerBuyBaseVolume() == null
                ? null
                : c.getTakerBuyBaseVolume().multiply(HUNDRED).divide(c.getVolume(), 2, RoundingMode.HALF_UP);
        m.put("takerBuyPercent", takerBuyPercent);
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> chart(String requestedSymbol, String requestedInterval, Instant from, Instant to) {
        String symbol = normalizeSymbol(requestedSymbol);
        String interval = normalizeChartInterval(requestedInterval);
        if (symbol == null) {
            throw new IllegalArgumentException("Symbol is required.");
        }
        if ((from == null) != (to == null)) {
            throw new IllegalArgumentException("Chart from/to must either both be supplied or both be omitted.");
        }
        if (from != null && !to.isAfter(from)) {
            throw new IllegalArgumentException("Chart 'to' must be after 'from'.");
        }

        /*
         * FIX-009 / Trade Inspector performance:
         * Never return the complete 1m history in one response. ApexCharts becomes
         * sluggish when tens of thousands of candlesticks are rendered together and
         * can make the whole page feel frozen. The browser now requests bounded
         * windows and lazily replaces the distant window while the user pans.
         *
         * Full-history NAVIGATION is preserved through firstOpenTime/lastOpenTime and
         * totalPointCount. Missing candles remain truthful gaps; nothing is synthesized.
         * This endpoint remains read-only and does not touch trading/replay state.
         */
        Candle first = candleRepository
                .findFirstBySymbolAndIntervalCodeAndClosedTrueOrderByOpenTimeAsc(symbol, interval)
                .orElse(null);
        Candle last = candleRepository
                .findFirstBySymbolAndIntervalCodeAndClosedTrueOrderByCloseTimeDesc(symbol, interval)
                .orElse(null);

        List<Candle> candles;
        if (from == null) {
            // Defensive fallback for direct API calls: return only the latest bounded block.
            if (last == null) {
                candles = List.of();
            } else {
                candles = new ArrayList<>(candleRepository.findClosedCandlesClosedAtOrBefore(
                        symbol, interval, last.getCloseTime(), PageRequest.of(0, 1200)));
                Collections.reverse(candles);
            }
        } else {
            candles = candleRepository
                    .findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeAsc(symbol, interval, from, to)
                    .stream().filter(Candle::isClosed).toList();
        }

        List<Map<String, Object>> rows = candles.stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("openTime", c.getOpenTime());
            row.put("closeTime", c.getCloseTime());
            row.put("openPrice", c.getOpenPrice());
            row.put("highPrice", c.getHighPrice());
            row.put("lowPrice", c.getLowPrice());
            row.put("closePrice", c.getClosePrice());
            row.put("volume", c.getVolume());
            row.put("quoteAssetVolume", c.getQuoteAssetVolume());
            row.put("numberOfTrades", c.getNumberOfTrades());
            row.put("takerBuyBaseVolume", c.getTakerBuyBaseVolume());
            BigDecimal takerBuyPercent = c.getVolume() == null || c.getVolume().signum() == 0 || c.getTakerBuyBaseVolume() == null
                    ? BigDecimal.ZERO
                    : c.getTakerBuyBaseVolume().multiply(HUNDRED).divide(c.getVolume(), 4, RoundingMode.HALF_UP);
            row.put("takerBuyPercent", takerBuyPercent);
            return row;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("symbol", symbol);
        response.put("interval", interval);
        response.put("windowed", true);
        response.put("pointCount", rows.size());
        response.put("totalPointCount", candleRepository.countBySymbolAndIntervalCodeAndClosedTrue(symbol, interval));
        response.put("firstOpenTime", first == null ? null : first.getOpenTime());
        response.put("lastOpenTime", last == null ? null : last.getOpenTime());
        response.put("windowStart", candles.isEmpty() ? null : candles.getFirst().getOpenTime());
        response.put("windowEnd", candles.isEmpty() ? null : candles.getLast().getOpenTime());
        response.put("candles", rows);
        return response;
    }

    private String normalizeChartInterval(String interval) {
        String value = interval == null ? "1m" : interval.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "1m", "5m", "1h", "4h" -> value;
            default -> "1m";
        };
    }

    private BigDecimal percentChange(BigDecimal from, BigDecimal to) {
        if (from == null || to == null || from.signum() == 0) return null;
        return to.subtract(from).multiply(HUNDRED).divide(from, 8, RoundingMode.HALF_UP);
    }
    private BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private String normalizeSymbol(String symbol) { return symbol == null || symbol.isBlank() || "ALL".equalsIgnoreCase(symbol) ? null : symbol.trim().toUpperCase(Locale.ROOT); }
    private String formatPct(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%"; }
    private record ExitAssessment(String quality, String explanation) {}

    private record TimeWindow(Instant from, Instant to) {
    }

}
