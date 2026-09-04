package com.crypto.position.service;

import com.crypto.audit.service.ProductionExitAuditService;
import com.crypto.position.domain.PositionManagementEvent;
import com.crypto.position.repository.PositionManagementEventRepository;

import com.crypto.domain.PaperPosition;
import com.crypto.domain.PositionStatus;
import com.crypto.repository.PaperPositionRepository;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.domain.TradeSignal;
import com.crypto.wallet.domain.WalletManagedPosition;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import com.crypto.wallet.service.WalletAutoExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.Locale;

/**
 * Mechanical protection for an already-open position using live Binance prices.
 * TP / SL / Profit Lock must not wait for the next analysis signal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LivePositionProtectionService {
    private static final MathContext MC = MathContext.DECIMAL64;

    private final WalletManagedPositionRepository managedPositionRepository;
    private final PaperPositionRepository paperPositionRepository;
    private final DynamicProfitLockService dynamicProfitLockService;
    private final PositionContinuationPolicy continuationPolicy;
    private final PositionExitPolicy exitPolicy;
    private final TradeSignalRepository tradeSignalRepository;
    private final WalletAutoExecutionService walletAutoExecutionService;
    private final ProductionExitAuditService productionExitAuditService;
    private final PositionManagementEventRepository positionManagementEventRepository;
    private final NearTpFailureProtectionPolicy nearTpFailureProtectionPolicy;

    @Transactional
    public void onPrice(String symbolValue, BigDecimal price) {
        if (symbolValue == null || symbolValue.isBlank() || price == null || price.signum() <= 0) return;
        String symbol = symbolValue.trim().toUpperCase(Locale.ROOT);

        WalletManagedPosition managed = managedPositionRepository
                .findFirstBySymbolAndStatusOrderByOpenedAtDesc(symbol, "OPEN")
                .orElse(null);
        if (managed == null || managed.getQuantity() == null || managed.getQuantity().signum() <= 0) return;

        TradeSignal one = tradeSignalRepository.findTopBySymbolAndIntervalOrderByGeneratedAtDesc(symbol, "1m").orElse(null);
        TradeSignal five = tradeSignalRepository.findTopBySymbolAndIntervalOrderByGeneratedAtDesc(symbol, "5m").orElse(null);
        TradeSignal hour = tradeSignalRepository.findTopBySymbolAndIntervalOrderByGeneratedAtDesc(symbol, "1h").orElse(null);

        // Reaching TP is a management checkpoint, not an unconditional full exit.
        if (managed.getTakeProfitUsdt() != null && price.compareTo(managed.getTakeProfitUsdt()) >= 0) {
            // Pass the complete immutable BUY thesis. PositionContinuationPolicy and
            // PositionManagementService now evaluate the same deterioration pressure (FIX-011).
            PositionContinuationPolicy.Evaluation continuation = continuationPolicy.evaluate(one, five, hour,
                    managed.getEntryTrendScore(), managed.getEntryStructureScore(),
                    managed.getEntryMomentumScore(), managed.getEntryVolumeScore(),
                    managed.getEntryConfidence(), managed.getEntryTotalScore());
            if (continuation.extendTarget()) {
                BigDecimal distance = managed.getTakeProfitUsdt().subtract(managed.getAverageEntryPriceUsdt());
                BigDecimal oldTarget = managed.getTakeProfitUsdt();
                BigDecimal newTarget = oldTarget.add(distance.multiply(BigDecimal.valueOf(0.50), MC), MC);
                Instant changedAt = Instant.now();
                managed.setTakeProfitUsdt(newTarget);
                // FIX-11T: TP extension changes the planned entry->TP distance. Any old Near-TP
                // arm/rejection was relative to the superseded target and must be restarted.
                resetNearTpTrackingForNewRiskGeometry(managed);
                managed.setUpdatedAt(changedAt);
                managedPositionRepository.save(managed);

                // FIX-067: Production has two persisted views of the same open lifecycle:
                // wallet_managed_position drives live protection, while paper_position is also
                // evaluated when a fresh TradeSignal arrives.  Before this fix only the managed
                // position received a TAKE_PROFIT_EXTENDED update.  The next 1m signal could
                // therefore see the stale ORIGINAL paper TP and close the wallet early.
                // PEPE wallet #833 / managed position #552 proved the race: managed TP was
                // extended 0.000004146897 -> 0.000004165346, but the signal path sold at
                // 0.000004150 because paper_position still held 0.000004146897.
                // Keep both Production state holders synchronized inside this same transaction.
                PaperPosition synchronizedPaper = paperPositionRepository
                        .findBySymbolAndStatus(symbol, PositionStatus.OPEN)
                        .orElse(null);
                if (synchronizedPaper != null) {
                    synchronizedPaper.setTakeProfit(newTarget);
                    paperPositionRepository.save(synchronizedPaper);
                }

                // FIX-053: Persist every TP revision so Dashboard can show the actual
                // Production management path instead of only the latest target value.
                positionManagementEventRepository.save(PositionManagementEvent.builder()
                        .walletPositionId(managed.getId())
                        .symbol(symbol)
                        .eventType("TAKE_PROFIT_EXTENDED")
                        .oldValueUsdt(oldTarget)
                        .newValueUsdt(newTarget)
                        .marketPriceUsdt(price)
                        .reason(continuation.explanation())
                        .occurredAt(changedAt)
                        .build());
                log.info("Live TAKE_PROFIT extended: symbol={}, oldTarget={}, newTarget={}, reason={}", symbol, oldTarget, newTarget, continuation.explanation());
            } else if (walletAutoExecutionService.executeMechanicalExit(
                    symbol, price, "TAKE_PROFIT", continuation.explanation())) {
                closePaper(symbol, price, PositionStatus.CLOSED, "TAKE_PROFIT", continuation.explanation(), one);
                log.info("Live TAKE_PROFIT executed: symbol={}, price={}, target={}", symbol, price, managed.getTakeProfitUsdt());
            }
            return;
        }

        // Stop loss is absolute and is never softened by continuation logic.
        if (managed.getStopLossUsdt() != null && price.compareTo(managed.getStopLossUsdt()) <= 0) {
            if (walletAutoExecutionService.executeMechanicalExit(
                    symbol, price, "STOP_LOSS",
                    "Live price " + price + " reached stop loss " + managed.getStopLossUsdt())) {
                closePaper(symbol, price, PositionStatus.STOPPED, "STOP_LOSS",
                        "Live market price reached the configured stop loss.", one);
                log.info("Live STOP_LOSS executed: symbol={}, price={}, stop={}", symbol, price, managed.getStopLossUsdt());
            }
            return;
        }

        DynamicProfitLockService.Evaluation lock = dynamicProfitLockService.evaluatePrice(symbol, price);
        if (lock.triggered()) {
            BigDecimal hardProfitFloor = managed.getAverageEntryPriceUsdt().multiply(BigDecimal.valueOf(1.0005), MC);
            if (price.compareTo(hardProfitFloor) < 0) {
                String reason = lock.explanation() + " Hard protected-profit floor " + hardProfitFloor + " was breached.";
                if (walletAutoExecutionService.executeMechanicalExit(symbol, price, "PROFIT_LOCK_HARD_EXIT", reason)) {
                    closePaper(symbol, price, PositionStatus.CLOSED, "PROFIT_LOCK_HARD_EXIT", reason, one);
                }
                return;
            }

            PositionExitPolicy.Evaluation lockDecision = exitPolicy.evaluateProfitLockBreach(one, five, hour);
            if (lockDecision.exit()) {
                String reason = lock.explanation() + " " + lockDecision.explanation();
                if (walletAutoExecutionService.executeMechanicalExit(symbol, price, lockDecision.code(), reason)) {
                    closePaper(symbol, price, PositionStatus.CLOSED, lockDecision.code(), reason, one);
                    log.info("Live PROFIT_LOCK exit: symbol={}, price={}, lock={}, reason={}",
                            symbol, price, lock.lockPrice(), lockDecision.code());
                }
                return;
            }
            log.info("Live PROFIT_LOCK protected hold: symbol={}, price={}, lock={}, reason={}",
                    symbol, price, lock.lockPrice(), lockDecision.explanation());
        }

        // Normal signal exits remain active even when Profit Lock is disabled. Higher-timeframe
        // failure can therefore close a winner instead of waiting forever for an exact 1m SELL event.
        PositionExitPolicy.Evaluation normalExit = exitPolicy.evaluateNormalExit(one, five, hour);
        if (normalExit.exit()) {
            if (walletAutoExecutionService.executeMechanicalExit(symbol, price, normalExit.code(), normalExit.explanation())) {
                closePaper(symbol, price, PositionStatus.CLOSED, normalExit.code(), normalExit.explanation(), one);
                log.info("Live signal-driven exit: symbol={}, price={}, reason={}", symbol, price, normalExit.code());
            }
            // FIX-11T: any higher-priority position action owns this evaluation cycle.
            // Near-TP must never race another SELL or quantity-changing decision on the same tick.
            return;
        }

        // FIX-11T: Near-TP Failure Protection is deliberately LAST. TP/SL/profit-lock and
        // existing signal exits above retain their exact authority and always win the cycle.
        evaluateNearTpProtection(managed, price, one, five);
    }

    private void evaluateNearTpProtection(WalletManagedPosition managed, BigDecimal price,
                                          TradeSignal oneMinute, TradeSignal fiveMinute) {
        // FIX-11T evidence is explicitly time-safe. Normal Production logic above keeps its
        // existing signal lookup unchanged; Near-TP alone falls back to an at-or-before query
        // if the repository's newest row is future-dated relative to this live evaluation.
        Instant evaluatedAt = Instant.now();
        oneMinute = latestEligibleSignal(managed.getSymbol(), "1m", oneMinute, evaluatedAt);
        fiveMinute = latestEligibleSignal(managed.getSymbol(), "5m", fiveMinute, evaluatedAt);

        NearTpFailureProtectionPolicy.State state = new NearTpFailureProtectionPolicy.State(
                managed.getNearTpState() == null ? NearTpState.INACTIVE : managed.getNearTpState(),
                managed.getNearTpBestPrice(), managed.getNearTpBearishStreak(),
                managed.getNearTpLastOneMinuteSignalId(), managed.isNearTpHarvestUsed());

        NearTpFailureProtectionPolicy.Evaluation evaluation = nearTpFailureProtectionPolicy.evaluate(
                state, managed.getAverageEntryPriceUsdt(), managed.getTakeProfitUsdt(),
                price, evaluatedAt, oneMinute, fiveMinute);

        NearTpFailureProtectionPolicy.State next = evaluation.state();
        boolean stateChanged = managed.getNearTpState() != next.nearTpState()
                || !sameDecimal(managed.getNearTpBestPrice(), next.bestPrice())
                || managed.getNearTpBearishStreak() != next.consecutiveBearishOneMinute()
                || !java.util.Objects.equals(managed.getNearTpLastOneMinuteSignalId(), next.lastEvaluatedOneMinuteSignalId());
        if (stateChanged) {
            managed.setNearTpState(next.nearTpState());
            managed.setNearTpBestPrice(next.bestPrice());
            managed.setNearTpBearishStreak(next.consecutiveBearishOneMinute());
            managed.setNearTpLastOneMinuteSignalId(next.lastEvaluatedOneMinuteSignalId());
            managed.setUpdatedAt(Instant.now());
            managedPositionRepository.save(managed);
        }

        if (evaluation.transition() || stateChanged) {
            log.info("FIX-11T Near-TP evaluation: positionId={}, symbol={}, state={}, code={}, price={}, bestPrice={}, " +
                            "tpProgressPct={}, givebackPct={}, bearish1mStreak={}, oneMinuteId={}, oneMinuteOriginal={}, " +
                            "fiveMinuteId={}, fiveMinuteOriginal={}, detail={}",
                    managed.getId(), managed.getSymbol(), next.nearTpState(), evaluation.code(), price, next.bestPrice(),
                    evaluation.tpProgressPercent(), evaluation.givebackPercent(), next.consecutiveBearishOneMinute(),
                    oneMinute == null ? null : oneMinute.getId(),
                    oneMinute == null ? null : oneMinute.getOriginalDecision(),
                    fiveMinute == null ? null : fiveMinute.getId(),
                    fiveMinute == null ? null : fiveMinute.getOriginalDecision(),
                    evaluation.explanation());
        } else if (log.isDebugEnabled() && evaluation.code().startsWith("HOLD_")) {
            log.debug("FIX-11T Near-TP hold: positionId={}, symbol={}, code={}, price={}, detail={}",
                    managed.getId(), managed.getSymbol(), evaluation.code(), price, evaluation.explanation());
        }

        if (!evaluation.harvestEligible()) return;

        WalletAutoExecutionService.PartialHarvestResult harvest = walletAutoExecutionService
                .executeNearTpPartialHarvest(managed.getSymbol(), price, evaluation.explanation());
        if (!harvest.executed()) {
            log.warn("FIX-11T Near-TP harvest not executed: positionId={}, symbol={}, status={}, price={}",
                    managed.getId(), managed.getSymbol(), harvest.status(), price);
            // If execution did not happen, return to rejection monitoring rather than leaving
            // the state stranded in FAILURE_CONFIRMED. Idempotency still lives in wallet_trade.
            managed.setNearTpState(NearTpState.NEAR_TP_REJECTION_DETECTED);
            if ("BELOW_BINANCE_MINIMUM".equals(harvest.status())
                    || "BINANCE_MINIMUM_UNAVAILABLE".equals(harvest.status())) {
                // The skipped order consumed the current confirmation pair. Requiring fresh
                // bearish evidence prevents a retry on every subsequent market-price tick.
                managed.setNearTpBearishStreak(0);
            }
            managed.setUpdatedAt(Instant.now());
            managedPositionRepository.save(managed);
            return;
        }

        // Keep paper_position quantity/P&L synchronized with the managed wallet lifecycle.
        // Without this, a later terminal signal close would calculate P&L on pre-harvest quantity.
        PaperPosition paper = paperPositionRepository.findBySymbolAndStatus(managed.getSymbol(), PositionStatus.OPEN).orElse(null);
        if (paper != null) {
            paper.setQuantity(harvest.remainingQuantity());
            BigDecimal priorRealized = paper.getRealizedPnl() == null ? BigDecimal.ZERO : paper.getRealizedPnl();
            paper.setRealizedPnl(priorRealized.add(harvest.realizedPnlUsdt(), MC));
            paperPositionRepository.save(paper);
        }

        log.info("FIX-11T NEAR_TP_PARTIAL_HARVESTED: positionId={}, symbol={}, soldQty={}, remainingQty={}, " +
                        "price={}, realizedPnl={}, realizedPnlPct={}",
                managed.getId(), managed.getSymbol(), harvest.soldQuantity(), harvest.remainingQuantity(),
                price, harvest.realizedPnlUsdt(), harvest.realizedPnlPercent());
    }

    private TradeSignal latestEligibleSignal(String symbol, String interval, TradeSignal newest, Instant evaluatedAt) {
        if (newest != null && newest.getGeneratedAt() != null && !newest.getGeneratedAt().isAfter(evaluatedAt)) {
            return newest;
        }
        return tradeSignalRepository
                .findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(symbol, interval, evaluatedAt)
                .orElse(null);
    }

    private void resetNearTpTrackingForNewRiskGeometry(WalletManagedPosition managed) {
        if (managed.isNearTpHarvestUsed()) return;
        managed.setNearTpState(NearTpState.INACTIVE);
        managed.setNearTpBestPrice(null);
        managed.setNearTpBearishStreak(0);
        managed.setNearTpLastOneMinuteSignalId(null);
    }

    private boolean sameDecimal(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private void closePaper(String symbol, BigDecimal exitPrice, PositionStatus status,
                            String closeReason, String explanation, TradeSignal sourceSignal) {
        PaperPosition paper = paperPositionRepository.findBySymbolAndStatus(symbol, PositionStatus.OPEN).orElse(null);
        if (paper == null) return;
        BigDecimal remainingPnl = exitPrice.subtract(paper.getEntryPrice(), MC).multiply(paper.getQuantity(), MC);
        // FIX-11T: paper_position may already contain realized P&L from a Near-TP partial
        // harvest. Preserve it when the remaining quantity is later closed.
        BigDecimal pnl = (paper.getRealizedPnl() == null ? BigDecimal.ZERO : paper.getRealizedPnl())
                .add(remainingPnl, MC);
        paper.setExitPrice(exitPrice);
        paper.setRealizedPnl(pnl);
        paper.setStatus(status);
        paper.setCloseReason(closeReason);
        paper.setExitReason(explanation);
        paper.setClosedAt(Instant.now());
        PaperPosition saved = paperPositionRepository.save(paper);

        // FIX-028: live-price exits (TP/SL/Profit Lock/normal exit) also write the
        // immutable audit record. sourceSignal is context only unless the closeTrigger
        // itself is a genuine SELL decision. No protection/exit policy is changed here.
        productionExitAuditService.record(saved, sourceSignal, closeReason, explanation);
    }
}
