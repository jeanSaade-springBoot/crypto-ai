package com.crypto.position.service;

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
    private final TradeSignalRepository tradeSignalRepository;
    private final WalletAutoExecutionService walletAutoExecutionService;

    @Transactional
    public void onPrice(String symbolValue, BigDecimal price) {
        if (symbolValue == null || symbolValue.isBlank() || price == null || price.signum() <= 0) return;
        String symbol = symbolValue.trim().toUpperCase(Locale.ROOT);

        WalletManagedPosition managed = managedPositionRepository
                .findFirstBySymbolAndStatusOrderByOpenedAtDesc(symbol, "OPEN")
                .orElse(null);
        if (managed == null || managed.getQuantity() == null || managed.getQuantity().signum() <= 0) return;

        // Reaching TP is a management checkpoint, not an unconditional full exit.
        if (managed.getTakeProfitUsdt() != null && price.compareTo(managed.getTakeProfitUsdt()) >= 0) {
            TradeSignal one = tradeSignalRepository.findTopBySymbolAndIntervalOrderByGeneratedAtDesc(symbol, "1m").orElse(null);
            TradeSignal five = tradeSignalRepository.findTopBySymbolAndIntervalOrderByGeneratedAtDesc(symbol, "5m").orElse(null);
            TradeSignal hour = tradeSignalRepository.findTopBySymbolAndIntervalOrderByGeneratedAtDesc(symbol, "1h").orElse(null);
            PositionContinuationPolicy.Evaluation continuation = continuationPolicy.evaluate(one, five, hour,
                    managed.getEntryTrendScore(), managed.getEntryMomentumScore(), managed.getEntryVolumeScore());
            if (continuation.extendTarget()) {
                BigDecimal distance = managed.getTakeProfitUsdt().subtract(managed.getAverageEntryPriceUsdt());
                BigDecimal oldTarget = managed.getTakeProfitUsdt();
                BigDecimal newTarget = oldTarget.add(distance.multiply(BigDecimal.valueOf(0.50), MC), MC);
                managed.setTakeProfitUsdt(newTarget);
                managed.setUpdatedAt(Instant.now());
                managedPositionRepository.save(managed);
                log.info("Live TAKE_PROFIT extended: symbol={}, oldTarget={}, newTarget={}, reason={}", symbol, oldTarget, newTarget, continuation.explanation());
            } else if (walletAutoExecutionService.executeMechanicalExit(
                    symbol, price, "TAKE_PROFIT", continuation.explanation())) {
                closePaper(symbol, price, PositionStatus.CLOSED, "TAKE_PROFIT", continuation.explanation());
                log.info("Live TAKE_PROFIT executed: symbol={}, price={}, target={}", symbol, price, managed.getTakeProfitUsdt());
            }
            return;
        }

        DynamicProfitLockService.Evaluation lock = dynamicProfitLockService.evaluatePrice(symbol, price);
        if (lock.triggered()) {
            if (walletAutoExecutionService.executeMechanicalExit(
                    symbol, price, "POSITION_PROFIT_LOCK", lock.explanation())) {
                closePaper(symbol, price, PositionStatus.CLOSED, "PROFIT_LOCK", lock.explanation());
                log.info("Live PROFIT_LOCK executed: symbol={}, price={}, lock={}", symbol, price, lock.lockPrice());
            }
            return;
        }

        if (managed.getStopLossUsdt() != null && price.compareTo(managed.getStopLossUsdt()) <= 0) {
            if (walletAutoExecutionService.executeMechanicalExit(
                    symbol, price, "STOP_LOSS",
                    "Live price " + price + " reached stop loss " + managed.getStopLossUsdt())) {
                closePaper(symbol, price, PositionStatus.STOPPED, "STOP_LOSS",
                        "Live market price reached the configured stop loss.");
                log.info("Live STOP_LOSS executed: symbol={}, price={}, stop={}", symbol, price, managed.getStopLossUsdt());
            }
        }
    }

    private void closePaper(String symbol, BigDecimal exitPrice, PositionStatus status,
                            String closeReason, String explanation) {
        PaperPosition paper = paperPositionRepository.findBySymbolAndStatus(symbol, PositionStatus.OPEN).orElse(null);
        if (paper == null) return;
        BigDecimal pnl = exitPrice.subtract(paper.getEntryPrice(), MC).multiply(paper.getQuantity(), MC);
        paper.setExitPrice(exitPrice);
        paper.setRealizedPnl(pnl);
        paper.setStatus(status);
        paper.setCloseReason(closeReason);
        paper.setExitReason(explanation);
        paper.setClosedAt(Instant.now());
        paperPositionRepository.save(paper);
    }
}
