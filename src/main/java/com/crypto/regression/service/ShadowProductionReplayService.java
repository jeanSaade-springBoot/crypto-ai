package com.crypto.regression.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.execution.domain.ExecutionOpportunity;
import com.crypto.execution.service.ExecutionIntelligenceService;
import com.crypto.execution.service.ExecutionReplayScope;
import com.crypto.execution.service.ExecutionPriceAuthorityService;
import com.crypto.position.service.PositionContinuationPolicy;
import com.crypto.position.service.NearTpFailureProtectionPolicy;
import com.crypto.position.service.NearTpState;
import com.crypto.position.service.PositionExitPolicy;
import com.crypto.position.service.ProfitLockPolicy;
import com.crypto.position.service.PositionPriceAuthorityPolicy;
import com.crypto.market.service.MarketPriceEventService;
import com.crypto.service.TradeExecutionValidationService;
import com.crypto.wallet.domain.WalletSettings;
import com.crypto.wallet.repository.WalletSettingsRepository;
import com.crypto.wallet.service.WalletExecutionSizingPolicy;
import com.crypto.wallet.service.BinanceMinimumExecutionPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShadowProductionReplayService {
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal INITIAL_CAPITAL = BigDecimal.valueOf(10000);

    private final JdbcTemplate jdbcTemplate;
    private final ExecutionIntelligenceService executionIntelligenceService;
    private final ExecutionReplayScope replayScope;
    private final ExecutionPriceAuthorityService executionPriceAuthorityService;
    private final PositionContinuationPolicy continuationPolicy;
    private final PositionExitPolicy exitPolicy;
    private final ProfitLockPolicy profitLockPolicy;
    private final TradeExecutionValidationService executionValidationService;
    private final WalletSettingsRepository walletSettingsRepository;
    private final WalletExecutionSizingPolicy executionSizingPolicy;
    private final BinanceMinimumExecutionPolicy binanceMinimumExecutionPolicy;
    private final PositionPriceAuthorityPolicy priceAuthorityPolicy;
    private final DefensiveRiskReductionReplayObserver defensiveRiskReductionObserver;
    private final OneCandleContinuationGraceReplayObserver oneCandleContinuationGraceReplayObserver;
    private final NearTpFailureProtectionPolicy nearTpFailureProtectionPolicy;

    public ReplayStats replay(long runId, String symbol, Instant executionStart, Instant executionEnd,
                              List<TradeSignal> generatedSignals,
                              List<MarketPriceEventService.PriceEvent> productionPriceEvents,
                              BooleanSupplier stopRequested) {
        List<TradeSignal> timeline = generatedSignals.stream()
                .filter(s -> s != null && s.getGeneratedAt() != null)
                .sorted(Comparator.comparing(TradeSignal::getGeneratedAt)
                        .thenComparingInt(s -> intervalOrder(s.getInterval())))
                .toList();

        TradeSignal latest1m = null;
        TradeSignal latest5m = null;
        TradeSignal latest1h = null;
        ShadowPosition open = null;
        BigDecimal cash = INITIAL_CAPITAL;
        BigDecimal realized = BigDecimal.ZERO;
        int trades = 0, wins = 0, losses = 0;
        int consecutiveFinalOneMinuteStrongSells = 0;
        WalletSettings replayWalletSettings = walletSettings();
        BigDecimal replayDailyBudget = executionSizingPolicy.initialDailyBudget(
                cash, replayWalletSettings.getMinimumUsdtReserve(),
                replayWalletSettings.getBaseTradeAmountUsdt(), replayWalletSettings.getMaximumDailyNewPositions());
        LocalDate replayTradeDate = null;
        int replayExecutedNewPositions = 0;
        // FIX-052: when Production live-price events exist, Replay uses them as the
        // authoritative mechanical-protection clock. Older windows created before V64
        // transparently retain candle/signal-close fallback and are therefore not claimed
        // as tick-exact parity runs.
        List<MarketPriceEventService.PriceEvent> livePrices = productionPriceEvents == null
                ? List.of()
                : productionPriceEvents.stream()
                    .filter(e -> e != null && e.observedAt() != null && e.price() != null)
                    .sorted(Comparator.comparing(MarketPriceEventService.PriceEvent::observedAt))
                    .toList();
        boolean exactPriceReplay = !livePrices.isEmpty();
        int livePriceIndex = 0;
        // FIX-109: normal Regression/Replay runs are Production-Parity by default.
        // Experimental rules require an explicitly opened EXPERIMENTAL scope.
        String replayLogicMode = ExecutionReplayScope.ReplayLogicMode.PRODUCTION_PARITY.name();
        defensiveRiskReductionObserver.logRunStart(runId, symbol);
        oneCandleContinuationGraceReplayObserver.startRun(runId, symbol);

        try (ExecutionReplayScope.Scope ignored = replayScope.open(runId, timeline,
                opportunity -> {
                    TradeSignal latest = opportunity == null ? null : opportunity.getLatestSignal();
                    if (latest != null && latest.getGeneratedAt() != null
                            && !latest.getGeneratedAt().isBefore(executionStart)
                            && !latest.getGeneratedAt().isAfter(executionEnd)) {
                        persistProductionOpportunity(runId, opportunity);
                    }
                })) {
        for (TradeSignal signal : timeline) {
            // FIX-090: shadow execution is frequently cancellable without affecting Production.
            if (stopRequested != null && stopRequested.getAsBoolean()) {
                throw new ReplayCancellationException(runId);
            }
            // FIX-052 exact ordering: BinanceKlineService invokes live position protection
            // from the 1m price update BEFORE publishing the candle-close analysis event.
            // Consume all persisted Production prices up to this signal timestamp before
            // making the freshly generated signal visible to position management.
            while (livePriceIndex < livePrices.size()
                    && (stopRequested == null || !stopRequested.getAsBoolean())
                    && !livePrices.get(livePriceIndex).observedAt().isAfter(signal.getGeneratedAt())) {
                MarketPriceEventService.PriceEvent live = livePrices.get(livePriceIndex++);
                replayScope.referencePrice(symbol, live.observedAt(), live.price());
                // FIX-11Q: advance only the isolated counterfactual grace position. This never mutates
                // the baseline Shadow position/cash/trades and therefore cannot change Replay parity.
                oneCandleContinuationGraceReplayObserver.onPrice(runId, symbol, live, latest1m, latest5m, latest1h);
                if (open != null && !live.observedAt().isBefore(executionStart) && !live.observedAt().isAfter(executionEnd)) {
                    LivePriceEvaluation protection = evaluateLivePrice(runId, symbol, open, live, latest1m, latest5m, latest1h);
                    open = protection.position();
                    if (protection.partialProceeds().signum() > 0) {
                        cash = cash.add(protection.partialProceeds(), MC);
                        realized = realized.add(protection.partialRealizedPnl(), MC);
                    }
                    ExitDecision liveExit = protection.decision();
                    if (liveExit.exit()) {
                        BigDecimal proceeds = live.price().multiply(open.quantity(), MC);
                        BigDecimal pnl = proceeds.subtract(open.cost(), MC);
                        BigDecimal pnlPct = percentage(open.entryPrice(), live.price());
                        BigDecimal totalPositionPnl = open.partialRealizedPnl().add(pnl, MC);
                        BigDecimal totalPositionPnlPct = totalPositionPnlPercent(open, pnl);
                        cash = cash.add(proceeds, MC);
                        realized = realized.add(pnl, MC);
                        trades++;
                        if (totalPositionPnl.signum() > 0) wins++; else if (totalPositionPnl.signum() < 0) losses++;
                        // wallet_execution_test stores this terminal leg; wallet_position_test stores
                        // the whole lifecycle including any earlier FIX-11T partial realization.
                        persistSellAtPrice(runId, symbol, live, latest1m, open, liveExit, pnl, pnlPct);
                        closePositionAtPrice(runId, open.positionId(), live, liveExit, totalPositionPnl, totalPositionPnlPct);
                        executionIntelligenceService.completePositionOpportunity(symbol, latest1m, liveExit.reason());
                                                open = null;
                    }
                }
            }

            replayScope.reference(signal.getGeneratedAt());
            if ("1m".equals(signal.getInterval())) {
                latest1m = signal;
                consecutiveFinalOneMinuteStrongSells = signal.getDecision() == SignalDecision.STRONG_SELL
                        ? consecutiveFinalOneMinuteStrongSells + 1 : 0;
            }
            if ("5m".equals(signal.getInterval())) latest5m = signal;
            if ("1h".equals(signal.getInterval())) latest1h = signal;
            oneCandleContinuationGraceReplayObserver.onSignal(runId, signal, latest1m, latest5m, latest1h);

            // FIX-11K Phase A: observe only. This runs beside the existing exit path and cannot
            // mutate quantity, cash, wallet_position_test, SELL authority, or Production state.
            // It records enough replay-native evidence to evaluate the 2/3/4 streak and
            // peak/giveback experiment matrix after the run instead of pre-selecting thresholds.
            if (open != null && "1m".equals(signal.getInterval())) {
                defensiveRiskReductionObserver.observe(
                        runId, open.positionId(), signal, latest5m, latest1h,
                        consecutiveFinalOneMinuteStrongSells, open.entryPrice(), open.highest());
            }

            if (open != null && !exactPriceReplay) {
                // Pre-V64 fallback only. Exact-parity runs receive mechanical protection from
                // the persisted Production live-price stream above.
                ExitDecision exit = evaluateExit(runId, open, signal, latest1m, latest5m, latest1h);
                if (exit.exit()) {
                    BigDecimal proceeds = signal.getLatestPrice().multiply(open.quantity(), MC);
                    BigDecimal pnl = proceeds.subtract(open.cost(), MC);
                    BigDecimal pnlPct = percentage(open.entryPrice(), signal.getLatestPrice());
                    BigDecimal totalPositionPnl = open.partialRealizedPnl().add(pnl, MC);
                    BigDecimal totalPositionPnlPct = totalPositionPnlPercent(open, pnl);
                    cash = cash.add(proceeds, MC);
                    realized = realized.add(pnl, MC);
                    trades++;
                    if (totalPositionPnl.signum() > 0) wins++; else if (totalPositionPnl.signum() < 0) losses++;
                    persistSell(runId, symbol, signal, open, exit, pnl, pnlPct);
                    closePosition(runId, open.positionId(), signal, exit, totalPositionPnl, totalPositionPnlPct);
                    // FIX-020 replay parity: a terminal replay exit consumes the same
                    // opportunity evidence boundary as production before any new BUY can form.
                    executionIntelligenceService.completePositionOpportunity(symbol, signal, exit.reason());
                                        open = null;
                    // Production PaperTradingService returns immediately after a signal-driven
                    // terminal close; it cannot reopen from that same signal invocation.
                    continue;
                } else {
                    if (exit.newTakeProfit() != null) {
                        open = open.withTakeProfit(exit.newTakeProfit());
                        persistNearTpState(open);
                    }
                    open = updateProfitLock(open, signal);
                    // FIX-11T: legacy signal-price fallback cannot be tick-exact, but it still
                    // executes the SAME Near-TP policy from the authoritative historical signal
                    // price so older Replay windows do not silently omit the Production rule.
                    if (priceAuthorityPolicy.canUseSignalPrice(signal, open.entryTime())) {
                        FallbackNearTpEvaluation nearTpFallback = evaluateNearTpAtSignalFallback(
                                runId, open, signal, latest1m, latest5m);
                        open = nearTpFallback.position();
                        if (nearTpFallback.partialProceeds().signum() > 0) {
                            cash = cash.add(nearTpFallback.partialProceeds(), MC);
                            realized = realized.add(nearTpFallback.partialRealizedPnl(), MC);
                        }
                    }
                    persistManagement(runId, signal, open.profitLockActive() ? "PROFIT_LOCK_ACTIVE" : "POSITION_HOLD",
                            open.takeProfit(), open.takeProfit(), open, exit.explanation());
                }
            }

            if (open != null && exactPriceReplay && "1m".equals(signal.getInterval()) && bearish(signal.getDecision())) {
                // Production's candle-close PaperTradingService has one additional SELL authority
                // after live-price protection: validate the newly generated 1m bearish signal.
                TradeExecutionValidationService.ValidationResult validatedSell = executionValidationService.validateSell(signal);
                if (validatedSell.allowed()) {
                    ExitDecision exit = new ExitDecision(true, validatedSell.code(), validatedSell.explanation());
                    BigDecimal proceeds = signal.getLatestPrice().multiply(open.quantity(), MC);
                    BigDecimal pnl = proceeds.subtract(open.cost(), MC);
                    BigDecimal pnlPct = percentage(open.entryPrice(), signal.getLatestPrice());
                    BigDecimal totalPositionPnl = open.partialRealizedPnl().add(pnl, MC);
                    BigDecimal totalPositionPnlPct = totalPositionPnlPercent(open, pnl);
                    cash = cash.add(proceeds, MC);
                    realized = realized.add(pnl, MC);
                    trades++;
                    if (totalPositionPnl.signum() > 0) wins++; else if (totalPositionPnl.signum() < 0) losses++;
                    persistSell(runId, symbol, signal, open, exit, pnl, pnlPct);
                    closePosition(runId, open.positionId(), signal, exit, totalPositionPnl, totalPositionPnlPct);
                    executionIntelligenceService.completePositionOpportunity(symbol, signal, exit.reason());
                                        open = null;
                    // Same production invocation cannot both SELL and immediately BUY again.
                    continue;
                }
            }

            // FIX-014 replay parity: production may use a fresh 5m BUY transition only to
            // wake an already-live deferred 1m opportunity. The actual execution/risk plan
            // remains the latest fresh 1m signal returned by the shared production service.
            // All other non-1m signals remain context-only exactly as before.
            TradeSignal executionSignal = signal;
            ExecutionIntelligenceService.ExecutionDecision decision;
            if ("1m".equals(signal.getInterval())) {
                int currentAllocation = open == null ? 0 : open.positionPercent();
                String currentStage = replayStage(currentAllocation);
                // FIX-026 parity: Recovery/Transition Entry is intentionally NOT reimplemented here.
                // Replay enters the exact production ExecutionIntelligenceService, which delegates to
                // RecoveryTransitionService using only candles closed as-of this historical signal.
                // This protects the ENA 12:55-13:04 KSA regression from replay-only future leakage.
                decision = executionIntelligenceService.evaluateBuy(signal, currentAllocation, currentStage);
            } else if ("5m".equals(signal.getInterval()) && open == null) {
                ExecutionIntelligenceService.SetupWakeupEvaluation wakeup =
                        executionIntelligenceService.evaluateSetupTimeframeWakeup(signal, 0);
                if (!wakeup.present()) {
                    // FIX-023 replay parity: use the exact same production confirmation wake-up
                    // after liquidity/HTF improves; 5m remains context-only execution authority.
                    wakeup = executionIntelligenceService.evaluateConfirmedSetupWakeup(signal, 0);
                }
                if (!wakeup.present()) continue;
                executionSignal = wakeup.executionSignal();
                decision = wakeup.decision();
            } else {
                continue;
            }

            // Warm-up evaluates the SAME production decision service so evidence/opportunity
            // memory is realistic at executionStart, but it never opens or adds to a wallet
            // position before the requested test window. The trigger timestamp (signal) controls
            // the replay window; executionSignal owns price/risk just like production.
            boolean inExecutionWindow = !signal.getGeneratedAt().isBefore(executionStart)
                    && !signal.getGeneratedAt().isAfter(executionEnd);
            LocalDate signalTradeDate = signal.getGeneratedAt().atZone(ZoneOffset.UTC).toLocalDate();
            if (!signalTradeDate.equals(replayTradeDate)) {
                replayTradeDate = signalTradeDate;
                replayExecutedNewPositions = 0;
                replayDailyBudget = executionSizingPolicy.initialDailyBudget(
                        cash, replayWalletSettings.getMinimumUsdtReserve(),
                        replayWalletSettings.getBaseTradeAmountUsdt(), replayWalletSettings.getMaximumDailyNewPositions());
            }
            // IMPORTANT: Proven/Regression never re-implements pressure readiness, normal BUY
            // routing, or FIX-014 wake-up rules. All are executed by the production
            // ExecutionIntelligenceService above; only wallet/opportunity persistence is shadowed.
            if (!inExecutionWindow) continue;

            if (open == null && decision.allowed()) {
                // FIX-056 Replay parity: resolve the same canonical execution-price authority
                // after the same Production decision gates, then revalidate Entry Quality at
                // that price before sizing. A replay without a fresh price event cannot claim
                // exact execution parity and therefore does not open the position here.
                ExecutionPriceAuthorityService.ExecutionPrice fresh = executionPriceAuthorityService
                        .resolve(symbol, signal.getGeneratedAt()).orElse(null);
                if (fresh == null) continue;
                decision = executionIntelligenceService.revalidateAtExecutionPrice(executionSignal, decision, fresh.price());
                if (!decision.allowed()) continue;
                int atrPercent = executionSignal.getAtrRecommendedPositionPercent() <= 0 ? 100 : executionSignal.getAtrRecommendedPositionPercent();
                int effectivePercent = Math.max(1, Math.min(100,
                        (int)Math.round(atrPercent * decision.positionPercent() / 100.0)));
                WalletExecutionSizingPolicy.Plan sizing = executionSizingPolicy.plan(
                        cash, replayWalletSettings.getMinimumUsdtReserve(), replayDailyBudget,
                        effectivePercent, 0, true, replayWalletSettings.getMaximumDailyNewPositions(),
                        replayExecutedNewPositions, fresh.price());
                BigDecimal budget = sizing.spend();
                if (sizing.allowed()) {
                    BigDecimal qty = sizing.quantity();
                    effectivePercent = sizing.normalizedPositionPercent();
                    long positionId = openPosition(runId, symbol, executionSignal, fresh, qty, budget, effectivePercent);
                    persistBuy(runId, symbol, executionSignal, fresh, qty, budget, effectivePercent, decision);
                    executionIntelligenceService.markExecuted(executionSignal, decision);
                    cash = cash.subtract(budget, MC);
                    replayExecutedNewPositions++;
                    // Keep the complete immutable BUY thesis needed by production continuation.
                    // Replay must not drop entry structure and silently evaluate a different exit policy.
                    open = new ShadowPosition(positionId, executionSignal.getGeneratedAt(), fresh.price(), qty, budget,
                            effectivePercent, executionSignal.getStopLoss(), executionSignal.getTakeProfit(), fresh.price(), false, null,
                            executionSignal.getTotalScore(), executionSignal.getConfidenceScore(), executionSignal.getTrendScore(), executionSignal.getTrendStructureScore(),
                            executionSignal.getMomentumScore(), executionSignal.getVolumeScore(),
                            NearTpState.INACTIVE, null, 0, null, false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
                    // FIX-112A: only an actually opened shadow position consumes the bullish entry.
                    replayScope.markEntryConsumed(executionSignal.getId());
                }
            } else if (open != null && decision.allowed()) {
                // FIX-056: progressive Replay adds use the same fresh execution price and
                // execution-time Entry Quality revalidation as Production.
                ExecutionPriceAuthorityService.ExecutionPrice fresh = executionPriceAuthorityService
                        .resolve(symbol, signal.getGeneratedAt()).orElse(null);
                if (fresh == null) continue;
                decision = executionIntelligenceService.revalidateAtExecutionPrice(signal, decision, fresh.price());
                if (!decision.allowed()) continue;
                int addPercent = Math.max(1, Math.min(100 - open.positionPercent(), decision.positionPercent()));
                WalletExecutionSizingPolicy.Plan sizing = executionSizingPolicy.plan(
                        cash, replayWalletSettings.getMinimumUsdtReserve(), replayDailyBudget,
                        addPercent, open.positionPercent(), false, replayWalletSettings.getMaximumDailyNewPositions(),
                        replayExecutedNewPositions, fresh.price());
                BigDecimal budget = sizing.spend();
                if (sizing.allowed()) {
                    addPercent = sizing.normalizedPositionPercent();
                    BigDecimal addedQty = sizing.quantity();
                    persistBuy(runId, symbol, signal, fresh, addedQty, budget, addPercent, decision);
                    executionIntelligenceService.markExecuted(signal, decision);
                    // FIX-112A: a progressive add consumes only its own triggering signal.
                    replayScope.markEntryConsumed(signal.getId());
                    cash = cash.subtract(budget, MC);
                    open = addToPosition(runId, open, signal, addedQty, budget, addPercent);
                }
            }
        }
        }

        BigDecimal finalWallet = cash;
        if (open != null && !timeline.isEmpty()) {
            BigDecimal mark = timeline.get(timeline.size() - 1).getLatestPrice();
            if (mark != null) finalWallet = finalWallet.add(mark.multiply(open.quantity(), MC), MC);
        }

        jdbcTemplate.update("""
                UPDATE analysis_test_run
                SET simulated_trade_count=?, simulated_win_count=?, simulated_loss_count=?,
                    simulated_realized_pnl=?, simulated_final_wallet=?
                WHERE id=?
                """, trades, wins, losses, realized, finalWallet, runId);
        defensiveRiskReductionObserver.logRunSummary(runId, symbol);
        oneCandleContinuationGraceReplayObserver.finishRun(runId, symbol, executionEnd);
        // FIX-109: expose whether this run had the persisted live-price stream required
        // for tick-exact production ordering. Older windows remain explicitly labelled
        // as fallback instead of being mistaken for exact parity.
        return new ReplayStats(trades, wins, losses, realized, finalWallet,
                exactPriceReplay ? "EXACT_PRICE_REPLAY" : "SIGNAL_PRICE_FALLBACK",
                replayLogicMode);
    }

    /**
     * FIX-052: mechanical position protection evaluated from the exact Production
     * 1m live-price observation stream. The order mirrors LivePositionProtectionService:
     * TAKE_PROFIT -> STOP_LOSS -> PROFIT_LOCK -> normal HTF exit. The newly closed
     * 1m signal is intentionally not visible yet; BinanceKlineService calls onPrice()
     * before publishing the candle-close event in Production.
     */
    private LivePriceEvaluation evaluateLivePrice(long runId, String symbol, ShadowPosition p,
                                                   MarketPriceEventService.PriceEvent event,
                                                   TradeSignal oneMinute, TradeSignal five, TradeSignal one) {
        BigDecimal price = event.price();
        ShadowPosition current = p;

        if (current.takeProfit() != null && price.compareTo(current.takeProfit()) >= 0) {
            PositionContinuationPolicy.Evaluation continuation = continuationPolicy.evaluate(
                    oneMinute, five, one,
                    current.entryTrend(), current.entryStructure(), current.entryMomentum(), current.entryVolume(),
                    current.entryConfidence(), current.entryScore());
            if (continuation.extendTarget()) {
                BigDecimal distance = current.takeProfit().subtract(current.entryPrice());
                BigDecimal oldTarget = current.takeProfit();
                BigDecimal newTarget = oldTarget.add(distance.multiply(BigDecimal.valueOf(0.50), MC), MC);
                current = current.withTakeProfit(newTarget);
                jdbcTemplate.update("UPDATE wallet_position_test SET take_profit_usdt=? WHERE id=?", newTarget, current.positionId());
                persistNearTpState(current);
                persistManagementAtPrice(runId, symbol, event, "TAKE_PROFIT_EXTENDED", oldTarget, newTarget, current, continuation.explanation());
                return new LivePriceEvaluation(ExitDecision.hold(), current);
            }
            // FIX-11Q: baseline continuation has already failed. Only now may the replay-only
            // counterfactual evaluate the one-candle grace. Existing PASS/TP-extension behavior above
            // always wins first and is never intercepted by this experiment.
            oneCandleContinuationGraceReplayObserver.observeBaselineTakeProfitFailure(
                    runId, symbol, current.positionId(), event.observedAt(), event.price(),
                    current.entryTime(), current.entryPrice(), current.quantity(), current.cost(),
                    current.stopLoss(), current.takeProfit(), current.highest(),
                    current.profitLockActive(), current.profitLockPrice(),
                    current.entryScore(), current.entryConfidence(), current.entryTrend(), current.entryStructure(),
                    current.entryMomentum(), current.entryVolume(), oneMinute, five, one, continuation.explanation());
            persistManagementAtPrice(runId, symbol, event, "TAKE_PROFIT_EXIT", current.takeProfit(), current.takeProfit(), current, continuation.explanation());
            return new LivePriceEvaluation(new ExitDecision(true, "TAKE_PROFIT", continuation.explanation()), current);
        }

        // Production stop-loss has priority over profit-lock evaluation.
        if (current.stopLoss() != null && price.compareTo(current.stopLoss()) <= 0) {
            return new LivePriceEvaluation(new ExitDecision(true, "STOP_LOSS",
                    "Live price " + price + " reached stop loss " + current.stopLoss()), current);
        }

        ShadowPosition updated = profitLockState(current, price);
        if (!equalsNullable(current.highest(), updated.highest())
                || current.profitLockActive() != updated.profitLockActive()
                || !equalsNullable(current.profitLockPrice(), updated.profitLockPrice())) {
            jdbcTemplate.update("UPDATE wallet_position_test SET highest_price_usdt=?, profit_lock_active=?, profit_lock_price_usdt=? WHERE id=?",
                    updated.highest(), updated.profitLockActive(), updated.profitLockPrice(), updated.positionId());
        }
        current = updated;

        if (current.profitLockActive() && current.profitLockPrice() != null
                && price.compareTo(current.profitLockPrice()) <= 0) {
            BigDecimal hardProfitFloor = current.entryPrice().multiply(BigDecimal.valueOf(1.0005), MC);
            if (price.compareTo(hardProfitFloor) < 0) {
                String reason = "Profit-lock hard floor was breached; protected profit can no longer be preserved. "
                        + profitLockConfigText();
                persistManagementAtPrice(runId, symbol, event, "PROFIT_LOCK_HARD_EXIT", current.takeProfit(), current.takeProfit(), current, reason);
                return new LivePriceEvaluation(new ExitDecision(true, "PROFIT_LOCK_HARD_EXIT", reason), current);
            }
            PositionExitPolicy.Evaluation lockDecision = exitPolicy.evaluateProfitLockBreach(oneMinute, five, one);
            if (lockDecision.exit()) {
                String reason = "Price retraced to the protected profit level after a profitable advance. "
                        + lockDecision.explanation() + " " + profitLockConfigText();
                persistManagementAtPrice(runId, symbol, event, "PROFIT_LOCK_EXIT", current.takeProfit(), current.takeProfit(), current, reason);
                return new LivePriceEvaluation(new ExitDecision(true, lockDecision.code(), reason), current);
            }
            persistManagementAtPrice(runId, symbol, event, "PROFIT_LOCK_HOLD", current.takeProfit(), current.takeProfit(), current,
                    lockDecision.explanation() + " " + profitLockConfigText());
        }

        PositionExitPolicy.Evaluation normalExit = exitPolicy.evaluateNormalExit(oneMinute, five, one);
        if (normalExit.exit()) {
            return new LivePriceEvaluation(new ExitDecision(true, normalExit.code(), normalExit.explanation()), current);
        }

        // FIX-11T: shared Production/Replay rule, evaluated LAST after all existing exits.
        NearTpFailureProtectionPolicy.State nearTpState = new NearTpFailureProtectionPolicy.State(
                current.nearTpState(), current.nearTpBestPrice(), current.nearTpBearishStreak(),
                current.nearTpLastOneMinuteSignalId(), current.nearTpHarvestUsed());
        NearTpFailureProtectionPolicy.Evaluation nearTp = nearTpFailureProtectionPolicy.evaluate(
                nearTpState, current.entryPrice(), current.takeProfit(), price, event.observedAt(), oneMinute, five);
        ShadowPosition nearTpUpdated = current.withNearTp(nearTp.state());
        if (!nearTpUpdated.sameNearTp(current)) {
            persistNearTpState(nearTpUpdated);
        }
        current = nearTpUpdated;
        if (nearTp.transition()) {
            log.info("FIX-11T Replay Near-TP: runId={}, positionId={}, symbol={}, code={}, state={}, price={}, best={}, givebackPct={}, bearish1mStreak={}, detail={}",
                    runId, current.positionId(), symbol, nearTp.code(), current.nearTpState(), price,
                    current.nearTpBestPrice(), nearTp.givebackPercent(), current.nearTpBearishStreak(), nearTp.explanation());
        }
        if (nearTp.harvestEligible()) {
            BigDecimal soldQty = current.quantity().multiply(BigDecimal.valueOf(0.50), MC);
            BinanceMinimumExecutionPolicy.Evaluation minimumCheck =
                    binanceMinimumExecutionPolicy.evaluate(symbol, soldQty, price);
            if (!minimumCheck.executable()) {
                // Same as Production: a below-minimum Binance order is not simulated. Reset
                // to rejection monitoring and let all existing management continue unchanged.
                current = current.withNearTp(new NearTpFailureProtectionPolicy.State(
                        NearTpState.NEAR_TP_REJECTION_DETECTED, current.nearTpBestPrice(),
                        0, current.nearTpLastOneMinuteSignalId(), false));
                persistNearTpState(current);
                log.info("FIX-11T Replay NEAR_TP_HARVEST_SKIPPED: runId={}, positionId={}, symbol={}, reason={}, requestedQty={}, requestedNotional={}, binanceMinimum={}; existing position management continues",
                        runId, current.positionId(), symbol, minimumCheck.code(), soldQty,
                        minimumCheck.requestedNotional(), minimumCheck.minimumNotional());
                return new LivePriceEvaluation(ExitDecision.hold(), current);
            }
            BigDecimal proceeds = soldQty.multiply(price, MC);
            BigDecimal soldCost = current.entryPrice().multiply(soldQty, MC);
            BigDecimal partialPnl = proceeds.subtract(soldCost, MC);
            BigDecimal partialPct = soldCost.signum() == 0 ? BigDecimal.ZERO : partialPnl
                    .divide(soldCost, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            current = current.withPartialHarvest(soldQty, soldCost, partialPnl);
            persistNearTpPartialHarvest(runId, symbol, event, oneMinute, current, soldQty, proceeds, partialPnl, partialPct, nearTp.explanation());
            log.info("FIX-11T Replay NEAR_TP_PARTIAL_HARVESTED: runId={}, positionId={}, symbol={}, soldQty={}, remainingQty={}, price={}, partialPnl={}",
                    runId, current.positionId(), symbol, soldQty, current.quantity(), price, partialPnl);
            return new LivePriceEvaluation(ExitDecision.hold(), current, proceeds, partialPnl);
        }
        return new LivePriceEvaluation(ExitDecision.hold(), current);
    }

    private ExitDecision evaluateExit(long runId, ShadowPosition p, TradeSignal s, TradeSignal oneMinute, TradeSignal five, TradeSignal one) {
        BigDecimal price = s.getLatestPrice();
        if (price == null) return ExitDecision.hold();

        // Replay uses the exact same temporal price-authority rule as production. A signal
        // generated later may still carry a candle close from before entry; that historical
        // price may update MTF context but may not retroactively hit TP/SL/profit-lock.
        boolean authoritativePrice = priceAuthorityPolicy.canUseSignalPrice(s, p.entryTime());
        if (authoritativePrice && p.takeProfit() != null && price.compareTo(p.takeProfit()) >= 0) {
            // Exact production TP-continuation call, including immutable entry structure.
            // FIX-011 regression relies on Replay and live using this same shared policy.
            PositionContinuationPolicy.Evaluation continuation = continuationPolicy.evaluate(
                    oneMinute != null ? oneMinute : s, five, one,
                    p.entryTrend(), p.entryStructure(), p.entryMomentum(), p.entryVolume(),
                    p.entryConfidence(), p.entryScore());
            if (continuation.extendTarget()) {
                BigDecimal distance = p.takeProfit().subtract(p.entryPrice());
                BigDecimal newTarget = p.takeProfit().add(distance.multiply(BigDecimal.valueOf(0.50), MC), MC);
                persistManagement(runId, s, "TAKE_PROFIT_EXTENDED", p.takeProfit(), newTarget, p, continuation.explanation());
                jdbcTemplate.update("UPDATE wallet_position_test SET take_profit_usdt=? WHERE id=?", newTarget, p.positionId());
                return new ExitDecision(false, "EXTEND_TAKE_PROFIT", continuation.explanation(), newTarget);
            }
            oneCandleContinuationGraceReplayObserver.observeBaselineTakeProfitFailure(
                    runId, s.getSymbol(), p.positionId(), s.getGeneratedAt(), price,
                    p.entryTime(), p.entryPrice(), p.quantity(), p.cost(), p.stopLoss(), p.takeProfit(), p.highest(),
                    p.profitLockActive(), p.profitLockPrice(), p.entryScore(), p.entryConfidence(), p.entryTrend(),
                    p.entryStructure(), p.entryMomentum(), p.entryVolume(), oneMinute != null ? oneMinute : s, five, one,
                    continuation.explanation());
            persistManagement(runId, s, "TAKE_PROFIT_EXIT", p.takeProfit(), p.takeProfit(), p, continuation.explanation());
            return new ExitDecision(true, "TAKE_PROFIT", continuation.explanation());
        }
        // FIX-052 fallback ordering also mirrors Production: STOP_LOSS is absolute and
        // is evaluated before profit-lock. Exact V64 runs normally take this path from
        // persisted live-price events rather than from signal-carried prices.
        if (authoritativePrice && p.stopLoss() != null && price.compareTo(p.stopLoss()) <= 0)
            return new ExitDecision(true, "STOP_LOSS", "Price reached the stored stop loss.");

        ShadowPosition updated = authoritativePrice ? profitLockState(p, price) : p;
        BigDecimal minimumProfitableExit = p.entryPrice().multiply(BigDecimal.valueOf(1.0005));
        if (authoritativePrice && updated.profitLockActive() && updated.profitLockPrice() != null
                && price.compareTo(updated.profitLockPrice()) <= 0) {
            if (price.compareTo(minimumProfitableExit) < 0) {
                String floorReason = "Profit-lock hard floor was breached; protected profit can no longer be preserved. "
                        + profitLockConfigText();
                persistManagement(runId, s, "PROFIT_LOCK_HARD_EXIT", p.takeProfit(), p.takeProfit(), updated, floorReason);
                return new ExitDecision(true, "PROFIT_LOCK_HARD_EXIT", floorReason);
            }
            PositionExitPolicy.Evaluation lockDecision = exitPolicy.evaluateProfitLockBreach(
                    oneMinute != null ? oneMinute : s, five, one);
            if (!lockDecision.exit()) {
                String holdReason = lockDecision.explanation() + " " + profitLockConfigText();
                persistManagement(runId, s, "PROFIT_LOCK_HOLD", p.takeProfit(), p.takeProfit(), updated, holdReason);
                return ExitDecision.hold();
            }
            String lockReason = "Price retraced to the protected profit level after a profitable advance. "
                    + lockDecision.explanation() + " " + profitLockConfigText();
            persistManagement(runId, s, "PROFIT_LOCK_EXIT", p.takeProfit(), p.takeProfit(), updated, lockReason);
            return new ExitDecision(true, lockDecision.code(), lockReason);
        }
        // Production has two SELL authorities: the live HTF PositionExitPolicy and the
        // 1m signal TradeExecutionValidationService. Replay executes both shared production
        // policies in the same order instead of carrying a test-only SELL rule.
        PositionExitPolicy.Evaluation normalExit = exitPolicy.evaluateNormalExit(
                oneMinute != null ? oneMinute : s, five, one);
        if (normalExit.exit()) {
            return new ExitDecision(true, normalExit.code(), normalExit.explanation());
        }
        if ("1m".equals(s.getInterval()) && bearish(s.getDecision())) {
            TradeExecutionValidationService.ValidationResult validatedSell = executionValidationService.validateSell(s);
            if (validatedSell.allowed()) {
                return new ExitDecision(true, validatedSell.code(), validatedSell.explanation());
            }
        }
        return ExitDecision.hold();
    }

    private FallbackNearTpEvaluation evaluateNearTpAtSignalFallback(long runId, ShadowPosition current,
                                                                    TradeSignal signal, TradeSignal oneMinute,
                                                                    TradeSignal fiveMinute) {
        NearTpFailureProtectionPolicy.State state = new NearTpFailureProtectionPolicy.State(
                current.nearTpState(), current.nearTpBestPrice(), current.nearTpBearishStreak(),
                current.nearTpLastOneMinuteSignalId(), current.nearTpHarvestUsed());
        NearTpFailureProtectionPolicy.Evaluation evaluation = nearTpFailureProtectionPolicy.evaluate(
                state, current.entryPrice(), current.takeProfit(), signal.getLatestPrice(), signal.getGeneratedAt(),
                oneMinute, fiveMinute);
        ShadowPosition updated = current.withNearTp(evaluation.state());
        if (!updated.sameNearTp(current)) persistNearTpState(updated);
        if (evaluation.transition()) {
            log.info("FIX-11T Replay fallback Near-TP: runId={}, positionId={}, symbol={}, code={}, state={}, price={}, givebackPct={}, bearish1mStreak={}, detail={}",
                    runId, updated.positionId(), signal.getSymbol(), evaluation.code(), updated.nearTpState(),
                    signal.getLatestPrice(), evaluation.givebackPercent(), updated.nearTpBearishStreak(), evaluation.explanation());
        }
        if (!evaluation.harvestEligible()) return new FallbackNearTpEvaluation(updated, BigDecimal.ZERO, BigDecimal.ZERO);

        BigDecimal soldQty = updated.quantity().multiply(BigDecimal.valueOf(0.50), MC);
        BinanceMinimumExecutionPolicy.Evaluation minimumCheck =
                binanceMinimumExecutionPolicy.evaluate(signal.getSymbol(), soldQty, signal.getLatestPrice());
        if (!minimumCheck.executable()) {
            updated = updated.withNearTp(new NearTpFailureProtectionPolicy.State(
                    NearTpState.NEAR_TP_REJECTION_DETECTED, updated.nearTpBestPrice(),
                    0, updated.nearTpLastOneMinuteSignalId(), false));
            persistNearTpState(updated);
            log.info("FIX-11T Replay fallback NEAR_TP_HARVEST_SKIPPED: runId={}, positionId={}, symbol={}, reason={}, requestedQty={}, requestedNotional={}, binanceMinimum={}; existing position management continues",
                    runId, updated.positionId(), signal.getSymbol(), minimumCheck.code(), soldQty,
                    minimumCheck.requestedNotional(), minimumCheck.minimumNotional());
            return new FallbackNearTpEvaluation(updated, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal proceeds = soldQty.multiply(signal.getLatestPrice(), MC);
        BigDecimal soldCost = updated.entryPrice().multiply(soldQty, MC);
        BigDecimal pnl = proceeds.subtract(soldCost, MC);
        BigDecimal pnlPct = soldCost.signum() == 0 ? BigDecimal.ZERO : pnl.divide(soldCost, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        updated = updated.withPartialHarvest(soldQty, soldCost, pnl);
        persistNearTpPartialHarvestAtSignal(runId, signal, updated, soldQty, proceeds, pnl, pnlPct, evaluation.explanation());
        log.info("FIX-11T Replay fallback NEAR_TP_PARTIAL_HARVESTED: runId={}, positionId={}, symbol={}, soldQty={}, remainingQty={}, price={}, partialPnl={}",
                runId, updated.positionId(), signal.getSymbol(), soldQty, updated.quantity(), signal.getLatestPrice(), pnl);
        return new FallbackNearTpEvaluation(updated, proceeds, pnl);
    }

    private ShadowPosition updateProfitLock(ShadowPosition p, TradeSignal s) {
        if (s.getLatestPrice() == null || !priceAuthorityPolicy.canUseSignalPrice(s, p.entryTime())) return p;
        ShadowPosition n = profitLockState(p, s.getLatestPrice());
        if (!equalsNullable(p.highest(), n.highest()) || p.profitLockActive() != n.profitLockActive()
                || !equalsNullable(p.profitLockPrice(), n.profitLockPrice())) {
            jdbcTemplate.update("UPDATE wallet_position_test SET highest_price_usdt=?, profit_lock_active=?, profit_lock_price_usdt=? WHERE id=?",
                    n.highest(), n.profitLockActive(), n.profitLockPrice(), p.positionId());
        }
        return n;
    }

    private ShadowPosition profitLockState(ShadowPosition p, BigDecimal price) {
        WalletSettings settings = walletSettings();
        ProfitLockPolicy.State state = profitLockPolicy.evaluate(
                p.entryPrice(), p.takeProfit(), price, p.highest(),
                p.profitLockActive(), p.profitLockPrice(),
                settings.isDynamicProfitLockEnabled(),
                nvl(settings.getProfitLockActivationPercent(), BigDecimal.valueOf(70)),
                nvl(settings.getProfitLockInitialPercent(), BigDecimal.valueOf(40)),
                nvl(settings.getProfitLockTrailStepPercent(), BigDecimal.valueOf(10)));
        return p.withLock(state.highestPrice(), state.active(), state.lockPrice());
    }

    private void persistManagement(long runId, TradeSignal s, String code, BigDecimal oldTp, BigDecimal newTp, ShadowPosition p, String explanation) {
        jdbcTemplate.update("""
            INSERT INTO position_management_test
            (test_run_id,symbol,generated_at,action_code,current_price,old_take_profit,new_take_profit,highest_price,profit_lock_active,profit_lock_price,explanation)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """, runId, s.getSymbol(), Timestamp.from(s.getGeneratedAt()), code, s.getLatestPrice(), oldTp, newTp,
                p.highest(), p.profitLockActive(), p.profitLockPrice(), explanation);
    }

    private void persistManagementAtPrice(long runId, String symbol, MarketPriceEventService.PriceEvent event,
                                          String code, BigDecimal oldTp, BigDecimal newTp,
                                          ShadowPosition p, String explanation) {
        jdbcTemplate.update("""
            INSERT INTO position_management_test
            (test_run_id,symbol,generated_at,action_code,current_price,old_take_profit,new_take_profit,highest_price,profit_lock_active,profit_lock_price,explanation)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """, runId, symbol, Timestamp.from(event.observedAt()), code, event.price(), oldTp, newTp,
                p.highest(), p.profitLockActive(), p.profitLockPrice(), explanation);
    }

    private String replayStage(int allocationPercent) {
        if (allocationPercent <= 0) return "NONE";
        if (allocationPercent < 50) return "SCOUT_ENTRY";
        if (allocationPercent < 100) return "CONFIRMATION_ADD";
        return "TREND_ADD";
    }

    private ShadowPosition addToPosition(long runId, ShadowPosition open, TradeSignal signal,
                                         BigDecimal addedQty, BigDecimal addedCost, int addPercent) {
        BigDecimal newQuantity = open.quantity().add(addedQty, MC);
        BigDecimal newCost = open.cost().add(addedCost, MC);
        BigDecimal newEntry = newCost.divide(newQuantity, 12, RoundingMode.HALF_UP);
        BigDecimal newStop = open.stopLoss();
        if (signal.getStopLoss() != null && (newStop == null || signal.getStopLoss().compareTo(newStop) > 0)) {
            newStop = signal.getStopLoss();
        }
        BigDecimal newTakeProfit = open.takeProfit();
        if (signal.getTakeProfit() != null && (newTakeProfit == null || signal.getTakeProfit().compareTo(newTakeProfit) > 0)) {
            newTakeProfit = signal.getTakeProfit();
        }
        int newPercent = Math.min(100, open.positionPercent() + addPercent);
        jdbcTemplate.update("""
                UPDATE wallet_position_test
                SET entry_price=?, quantity=?, total_cost_usdt=?, position_percent=?,
                    stop_loss_usdt=?, take_profit_usdt=?
                WHERE id=? AND test_run_id=?
                """, newEntry, newQuantity, newCost, newPercent, newStop, newTakeProfit,
                open.positionId(), runId);
        ShadowPosition updated = open.withAdd(newEntry, newQuantity, newCost, newPercent, newStop, newTakeProfit);
        persistNearTpState(updated);
        return updated;
    }

    private void persistProductionOpportunity(long runId, ExecutionOpportunity o) {
        TradeSignal s = o.getLatestSignal();
        if (s == null || s.getGeneratedAt() == null) return;
        jdbcTemplate.update("""
            INSERT INTO execution_opportunity_test
            (test_run_id, source_signal_id, symbol, generated_at, replay_stage, evidence_count, buy_count, watch_count,
             neutral_count, bearish_count, evidence_score, opportunity_health, peak_score, peak_confidence, peak_decision,
             peak_regime, peak_btc_status, peak_liquidity_status, peak_observed_at, recommended_position_percent,
             current_final_decision, current_original_decision, five_minute_decision, one_hour_decision,
             old_hard_bearish_reversal, corrected_hard_bearish_reversal, decision_code, decision_explanation)
            VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?)
            """, runId, s.getSymbol(), Timestamp.from(s.getGeneratedAt()), o.getStatus(), o.getEvidenceCount(),
                o.getBuyCount(), o.getWatchCount(), o.getNeutralCount(), o.getBearishCount(), o.getEvidenceScore(),
                o.getOpportunityHealth(), o.getPeakScore(), o.getPeakConfidence(), o.getPeakDecision(), o.getPeakRegime(),
                o.getPeakBtcStatus(), o.getPeakLiquidityStatus(), o.getPeakObservedAt() == null ? null : Timestamp.from(o.getPeakObservedAt()),
                o.getRecommendedPositionPercent(), name(s.getDecision()), name(s.getOriginalDecision()),
                o.getFiveMinuteDecision(), o.getOneHourDecision(), o.getDecisionCode(), o.getDecisionExplanation());
    }

    private void persistBuy(long runId,String symbol,TradeSignal s,ExecutionPriceAuthorityService.ExecutionPrice fresh,BigDecimal qty,BigDecimal budget,int pct,ExecutionIntelligenceService.ExecutionDecision d){
        jdbcTemplate.update("""
            INSERT INTO wallet_execution_test
            (test_run_id,symbol,side,execution_time,execution_price,quantity,notional_usdt,position_percent,signal_interval,signal_decision,execution_source,execution_code,execution_reason)
            VALUES (?,?,'BUY',?,?,?,?,?,?,?,?,?,?)""",
            runId,symbol,Timestamp.from(fresh.observedAt()),fresh.price(),qty,budget,pct,s.getInterval(),name(s.getDecision()),d.source(),d.code(),d.explanation() + " DecisionPrice=" + s.getLatestPrice() + ", executionPriceObservedAt=" + fresh.observedAt());
    }

    private long openPosition(long runId,String symbol,TradeSignal s,ExecutionPriceAuthorityService.ExecutionPrice fresh,BigDecimal qty,BigDecimal budget,int pct){
        org.springframework.jdbc.support.GeneratedKeyHolder kh=new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(c->{var ps=c.prepareStatement("""
            INSERT INTO wallet_position_test
            (test_run_id,symbol,status,entry_time,entry_price,quantity,total_cost_usdt,position_percent,stop_loss_usdt,take_profit_usdt,highest_price_usdt)
            VALUES (?,?,'OPEN',?,?,?,?,?,?,?,?)""",java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1,runId);ps.setString(2,symbol);ps.setTimestamp(3,Timestamp.from(fresh.observedAt()));ps.setBigDecimal(4,fresh.price());ps.setBigDecimal(5,qty);ps.setBigDecimal(6,budget);ps.setInt(7,pct);ps.setBigDecimal(8,s.getStopLoss());ps.setBigDecimal(9,s.getTakeProfit());ps.setBigDecimal(10,fresh.price());return ps;},kh);
        if (kh.getKey() == null) throw new IllegalStateException("Could not create shadow position");
        return kh.getKey().longValue();
    }

    private void persistSell(long runId,String symbol,TradeSignal s,ShadowPosition p,ExitDecision e,BigDecimal pnl,BigDecimal pnlPct){
        BigDecimal notional=s.getLatestPrice().multiply(p.quantity(),MC);
        jdbcTemplate.update("""
            INSERT INTO wallet_execution_test
            (test_run_id,symbol,side,execution_time,execution_price,quantity,notional_usdt,position_percent,signal_interval,signal_decision,execution_source,execution_code,execution_reason,realized_pnl_usdt,realized_pnl_percent)
            VALUES (?,?,'SELL',?,?,?,?,0,?,?,?,?,?,?,?)""",
            runId,symbol,Timestamp.from(s.getGeneratedAt()),s.getLatestPrice(),p.quantity(),notional,s.getInterval(),name(s.getDecision()),"POSITION_MANAGEMENT",e.reason(),e.explanation(),pnl,pnlPct);
    }

    private void closePosition(long runId,long id,TradeSignal s,ExitDecision e,BigDecimal pnl,BigDecimal pnlPct){
        jdbcTemplate.update("""
            UPDATE wallet_position_test SET status='CLOSED',exit_time=?,exit_price=?,exit_reason=?,exit_explanation=?,realized_pnl_usdt=?,realized_pnl_percent=? WHERE id=? AND test_run_id=?""",
                Timestamp.from(s.getGeneratedAt()),s.getLatestPrice(),e.reason(),e.explanation(),pnl,pnlPct,id,runId);
    }

    private void persistSellAtPrice(long runId, String symbol, MarketPriceEventService.PriceEvent event,
                                    TradeSignal contextSignal, ShadowPosition p, ExitDecision e,
                                    BigDecimal pnl, BigDecimal pnlPct) {
        BigDecimal notional = event.price().multiply(p.quantity(), MC);
        jdbcTemplate.update("""
            INSERT INTO wallet_execution_test
            (test_run_id,symbol,side,execution_time,execution_price,quantity,notional_usdt,position_percent,signal_interval,signal_decision,execution_source,execution_code,execution_reason,realized_pnl_usdt,realized_pnl_percent)
            VALUES (?,?,'SELL',?,?,?,?,0,?,?,?,?,?,?,?)
            """, runId, symbol, Timestamp.from(event.observedAt()), event.price(), p.quantity(), notional,
                contextSignal == null ? null : contextSignal.getInterval(),
                contextSignal == null ? null : name(contextSignal.getDecision()),
                "LIVE_PRICE_PROTECTION", e.reason(), e.explanation(), pnl, pnlPct);
    }

    private void closePositionAtPrice(long runId, long id, MarketPriceEventService.PriceEvent event,
                                      ExitDecision e, BigDecimal pnl, BigDecimal pnlPct) {
        jdbcTemplate.update("""
            UPDATE wallet_position_test
            SET status='CLOSED',exit_time=?,exit_price=?,exit_reason=?,exit_explanation=?,realized_pnl_usdt=?,realized_pnl_percent=?
            WHERE id=? AND test_run_id=?
            """, Timestamp.from(event.observedAt()), event.price(), e.reason(), e.explanation(), pnl, pnlPct, id, runId);
    }

    private void persistNearTpPartialHarvestAtSignal(long runId, TradeSignal signal, ShadowPosition p,
                                                     BigDecimal soldQty, BigDecimal proceeds, BigDecimal pnl,
                                                     BigDecimal pnlPct, String explanation) {
        jdbcTemplate.update("""
            INSERT INTO wallet_execution_test
            (test_run_id,symbol,side,execution_time,execution_price,quantity,notional_usdt,position_percent,signal_interval,signal_decision,execution_source,execution_code,execution_reason,realized_pnl_usdt,realized_pnl_percent)
            VALUES (?,?,'SELL',?,?,?,?,0,?,?,?,?,?,?,?)
            """, runId, signal.getSymbol(), Timestamp.from(signal.getGeneratedAt()), signal.getLatestPrice(), soldQty, proceeds,
                signal.getInterval(), name(signal.getDecision()), "SIGNAL_PRICE_FALLBACK",
                "NEAR_TP_PARTIAL_HARVEST", explanation, pnl, pnlPct);
        jdbcTemplate.update("""
            UPDATE wallet_position_test
            SET quantity=?, total_cost_usdt=?, near_tp_state=?, near_tp_best_price=?, near_tp_bearish_streak=?,
                near_tp_last_1m_signal_id=?, near_tp_harvest_used=1, near_tp_harvested_quantity=?, realized_pnl_usdt=?
            WHERE id=?
            """, p.quantity(), p.cost(), p.nearTpState().name(), p.nearTpBestPrice(), p.nearTpBearishStreak(),
                p.nearTpLastOneMinuteSignalId(), p.nearTpHarvestedQuantity(), p.partialRealizedPnl(), p.positionId());
    }

    private void persistNearTpState(ShadowPosition p) {
        jdbcTemplate.update("""
            UPDATE wallet_position_test
            SET near_tp_state=?, near_tp_best_price=?, near_tp_bearish_streak=?, near_tp_last_1m_signal_id=?,
                near_tp_harvest_used=?, near_tp_harvested_quantity=?
            WHERE id=?
            """, p.nearTpState().name(), p.nearTpBestPrice(), p.nearTpBearishStreak(), p.nearTpLastOneMinuteSignalId(),
                p.nearTpHarvestUsed(), p.nearTpHarvestedQuantity(), p.positionId());
    }

    private void persistNearTpPartialHarvest(long runId, String symbol, MarketPriceEventService.PriceEvent event,
                                             TradeSignal contextSignal, ShadowPosition p, BigDecimal soldQty,
                                             BigDecimal proceeds, BigDecimal pnl, BigDecimal pnlPct, String explanation) {
        jdbcTemplate.update("""
            INSERT INTO wallet_execution_test
            (test_run_id,symbol,side,execution_time,execution_price,quantity,notional_usdt,position_percent,signal_interval,signal_decision,execution_source,execution_code,execution_reason,realized_pnl_usdt,realized_pnl_percent)
            VALUES (?,?,'SELL',?,?,?,?,0,?,?,?,?,?,?,?)
            """, runId, symbol, Timestamp.from(event.observedAt()), event.price(), soldQty, proceeds,
                contextSignal == null ? null : contextSignal.getInterval(),
                contextSignal == null ? null : name(contextSignal.getDecision()),
                "LIVE_PRICE_PROTECTION", "NEAR_TP_PARTIAL_HARVEST", explanation, pnl, pnlPct);
        jdbcTemplate.update("""
            UPDATE wallet_position_test
            SET quantity=?, total_cost_usdt=?, near_tp_state=?, near_tp_best_price=?, near_tp_bearish_streak=?,
                near_tp_last_1m_signal_id=?, near_tp_harvest_used=1, near_tp_harvested_quantity=?, realized_pnl_usdt=?
            WHERE id=?
            """, p.quantity(), p.cost(), p.nearTpState().name(), p.nearTpBestPrice(), p.nearTpBearishStreak(),
                p.nearTpLastOneMinuteSignalId(), p.nearTpHarvestedQuantity(), p.partialRealizedPnl(), p.positionId());
    }

    private WalletSettings walletSettings() {
        return walletSettingsRepository.findById(1L).orElseGet(() -> WalletSettings.builder()
                .id(1L)
                .dynamicProfitLockEnabled(true)
                .profitLockActivationPercent(BigDecimal.valueOf(70))
                .profitLockInitialPercent(BigDecimal.valueOf(40))
                .profitLockTrailStepPercent(BigDecimal.valueOf(10))
                .build());
    }

    private BigDecimal nvl(BigDecimal value, BigDecimal fallback) { return value == null ? fallback : value; }

    private String profitLockConfigText() {
        WalletSettings s = walletSettings();
        return "Admin Profit Lock: activation=" + nvl(s.getProfitLockActivationPercent(), BigDecimal.valueOf(70)).stripTrailingZeros().toPlainString()
                + "%, initial=" + nvl(s.getProfitLockInitialPercent(), BigDecimal.valueOf(40)).stripTrailingZeros().toPlainString()
                + "%, trail=" + nvl(s.getProfitLockTrailStepPercent(), BigDecimal.valueOf(10)).stripTrailingZeros().toPlainString() + "%.";
    }

    private BigDecimal totalPositionPnlPercent(ShadowPosition position, BigDecimal remainingLegPnl) {
        BigDecimal totalPnl = position.partialRealizedPnl().add(remainingLegPnl, MC);
        BigDecimal totalInvestedCost = position.cost().add(position.partialHarvestCostBasis(), MC);
        return totalInvestedCost.signum() == 0 ? BigDecimal.ZERO
                : totalPnl.divide(totalInvestedCost, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal percentage(BigDecimal entry,BigDecimal exit){return exit.subtract(entry).multiply(BigDecimal.valueOf(100)).divide(entry,8,RoundingMode.HALF_UP);}
    private boolean bullish(SignalDecision d){return d==SignalDecision.BUY||d==SignalDecision.STRONG_BUY;}
    private boolean bearish(SignalDecision d){return d==SignalDecision.SELL||d==SignalDecision.STRONG_SELL;}
    private String name(SignalDecision d){return d==null?null:d.name();}
    private int intervalOrder(String i){return "1h".equals(i)?0:"5m".equals(i)?1:2;}
    private boolean equalsNullable(BigDecimal a,BigDecimal b){return a==null?b==null:b!=null&&a.compareTo(b)==0;}

    public record ReplayStats(int trades, int wins, int losses, BigDecimal realizedPnl, BigDecimal finalWallet,
                              String priceReplayMode, String logicMode) {}
    private record FallbackNearTpEvaluation(ShadowPosition position, BigDecimal partialProceeds,
                                            BigDecimal partialRealizedPnl) {}
    private record LivePriceEvaluation(ExitDecision decision, ShadowPosition position,
                                       BigDecimal partialProceeds, BigDecimal partialRealizedPnl) {
        LivePriceEvaluation(ExitDecision decision, ShadowPosition position) {
            this(decision, position, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }
    private record ExitDecision(boolean exit,String reason,String explanation,BigDecimal newTakeProfit){
        ExitDecision(boolean exit,String reason,String explanation){this(exit,reason,explanation,null);}
        static ExitDecision hold(){return new ExitDecision(false,"HOLD","Position remains open.",null);}
    }
    private record ShadowPosition(long positionId, Instant entryTime, BigDecimal entryPrice, BigDecimal quantity,
                                  BigDecimal cost, int positionPercent, BigDecimal stopLoss, BigDecimal takeProfit,
                                  BigDecimal highest, boolean profitLockActive, BigDecimal profitLockPrice,
                                  int entryScore, int entryConfidence, int entryTrend, int entryStructure,
                                  int entryMomentum, int entryVolume, NearTpState nearTpState, BigDecimal nearTpBestPrice,
                                  int nearTpBearishStreak, Long nearTpLastOneMinuteSignalId, boolean nearTpHarvestUsed,
                                  BigDecimal nearTpHarvestedQuantity, BigDecimal partialRealizedPnl,
                                  BigDecimal partialHarvestCostBasis) {
        ShadowPosition withLock(BigDecimal h, boolean a, BigDecimal l) {
            return new ShadowPosition(positionId, entryTime, entryPrice, quantity, cost, positionPercent, stopLoss, takeProfit,
                    h, a, l, entryScore, entryConfidence, entryTrend, entryStructure, entryMomentum, entryVolume,
                    nearTpState, nearTpBestPrice, nearTpBearishStreak, nearTpLastOneMinuteSignalId, nearTpHarvestUsed,
                    nearTpHarvestedQuantity, partialRealizedPnl, partialHarvestCostBasis);
        }
        ShadowPosition withTakeProfit(BigDecimal tp) {
            NearTpState state = nearTpHarvestUsed ? nearTpState : NearTpState.INACTIVE;
            return new ShadowPosition(positionId, entryTime, entryPrice, quantity, cost, positionPercent, stopLoss, tp,
                    highest, profitLockActive, profitLockPrice, entryScore, entryConfidence, entryTrend, entryStructure, entryMomentum, entryVolume,
                    state, nearTpHarvestUsed ? nearTpBestPrice : null, nearTpHarvestUsed ? nearTpBearishStreak : 0,
                    nearTpHarvestUsed ? nearTpLastOneMinuteSignalId : null, nearTpHarvestUsed, nearTpHarvestedQuantity, partialRealizedPnl, partialHarvestCostBasis);
        }
        ShadowPosition withAdd(BigDecimal newEntry, BigDecimal newQuantity, BigDecimal newCost, int newPercent, BigDecimal newStop, BigDecimal newTakeProfit) {
            NearTpState state = nearTpHarvestUsed ? nearTpState : NearTpState.INACTIVE;
            return new ShadowPosition(positionId, entryTime, newEntry, newQuantity, newCost, newPercent, newStop, newTakeProfit,
                    highest, profitLockActive, profitLockPrice, entryScore, entryConfidence, entryTrend, entryStructure, entryMomentum, entryVolume,
                    state, nearTpHarvestUsed ? nearTpBestPrice : null, nearTpHarvestUsed ? nearTpBearishStreak : 0,
                    nearTpHarvestUsed ? nearTpLastOneMinuteSignalId : null, nearTpHarvestUsed, nearTpHarvestedQuantity, partialRealizedPnl, partialHarvestCostBasis);
        }
        ShadowPosition withNearTp(NearTpFailureProtectionPolicy.State state) {
            return new ShadowPosition(positionId, entryTime, entryPrice, quantity, cost, positionPercent, stopLoss, takeProfit,
                    highest, profitLockActive, profitLockPrice, entryScore, entryConfidence, entryTrend, entryStructure, entryMomentum, entryVolume,
                    state.nearTpState(), state.bestPrice(), state.consecutiveBearishOneMinute(), state.lastEvaluatedOneMinuteSignalId(),
                    state.harvestUsed(), nearTpHarvestedQuantity, partialRealizedPnl, partialHarvestCostBasis);
        }
        ShadowPosition withPartialHarvest(BigDecimal soldQty, BigDecimal soldCost, BigDecimal realizedPnl) {
            return new ShadowPosition(positionId, entryTime, entryPrice, quantity.subtract(soldQty, MC), cost.subtract(soldCost, MC),
                    positionPercent, stopLoss, takeProfit, highest, profitLockActive, profitLockPrice,
                    entryScore, entryConfidence, entryTrend, entryStructure, entryMomentum, entryVolume,
                    NearTpState.NEAR_TP_PARTIAL_HARVESTED, nearTpBestPrice, nearTpBearishStreak, nearTpLastOneMinuteSignalId, true,
                    nearTpHarvestedQuantity.add(soldQty, MC), partialRealizedPnl.add(realizedPnl, MC),
                    partialHarvestCostBasis.add(soldCost, MC));
        }
        boolean sameNearTp(ShadowPosition other) {
            return nearTpState == other.nearTpState
                    && equalsDecimal(nearTpBestPrice, other.nearTpBestPrice)
                    && nearTpBearishStreak == other.nearTpBearishStreak
                    && java.util.Objects.equals(nearTpLastOneMinuteSignalId, other.nearTpLastOneMinuteSignalId)
                    && nearTpHarvestUsed == other.nearTpHarvestUsed;
        }
        private boolean equalsDecimal(BigDecimal a, BigDecimal b) {
            return a == null ? b == null : b != null && a.compareTo(b) == 0;
        }
    }
}
