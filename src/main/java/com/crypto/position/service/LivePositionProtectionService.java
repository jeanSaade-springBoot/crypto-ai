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
                managed.setUpdatedAt(changedAt);
                managedPositionRepository.save(managed);
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
        }
    }

    private void closePaper(String symbol, BigDecimal exitPrice, PositionStatus status,
                            String closeReason, String explanation, TradeSignal sourceSignal) {
        PaperPosition paper = paperPositionRepository.findBySymbolAndStatus(symbol, PositionStatus.OPEN).orElse(null);
        if (paper == null) return;
        BigDecimal pnl = exitPrice.subtract(paper.getEntryPrice(), MC).multiply(paper.getQuantity(), MC);
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
