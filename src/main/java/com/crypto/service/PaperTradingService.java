package com.crypto.service;

import com.crypto.config.TradingProperties;
import com.crypto.domain.*;
import com.crypto.repository.PaperPositionRepository;
import com.crypto.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.crypto.wallet.service.WalletAutoExecutionService;
import com.crypto.wallet.repository.WalletTradeRepository;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import com.crypto.wallet.domain.WalletManagedPosition;
import com.crypto.execution.service.ExecutionIntelligenceService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.crypto.position.service.PositionManagementService;
import com.crypto.position.service.DynamicProfitLockService;
import com.crypto.position.service.PositionPriceAuthorityPolicy;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaperTradingService {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final TradingProperties properties;
    private final TradeSignalRepository signalRepository;
    private final PaperPositionRepository positionRepository;
    private final WalletAutoExecutionService walletAutoExecutionService;
    private final WalletTradeRepository walletTradeRepository;
    private final WalletManagedPositionRepository walletManagedPositionRepository;
    private final TradeExecutionValidationService executionValidationService;
    private final ExecutionIntelligenceService executionIntelligenceService;
    private final DynamicProfitLockService dynamicProfitLockService;
    private final PositionPriceAuthorityPolicy priceAuthorityPolicy;

    /** Advisory-only; optional injection preserves existing constructor-based tests. */
    @Autowired(required = false)
    private PositionManagementService positionManagementService;

    @Transactional
    public PaperPosition openFromLatestSignal(String symbol) {
        String normalized = normalizeSymbol(symbol);
        TradeSignal signal = signalRepository
                .findTopBySymbolOrderByGeneratedAtDesc(normalized)
                .orElseThrow(() -> new IllegalArgumentException("No signal found for " + normalized));

        return processSignal(signal)
                .orElseThrow(() -> new IllegalStateException(
                        "The latest signal did not open a new position. It may be WATCH/NEUTRAL/SELL, " +
                                "or a position for this symbol is already open."
                ));
    }

    /**
     * Applies the complete paper-trade lifecycle for one newly-created signal.
     * BUY opens one position, WATCH/NEUTRAL holds it, and SELL closes it.
     * Stop-loss and take-profit are checked before the signal decision.
     */
    @Transactional
    public Optional<PaperPosition> processSignal(TradeSignal signal) {
        if (signal == null) {
            throw new IllegalArgumentException("Trade signal is required");
        }

        String symbol = normalizeSymbol(signal.getSymbol());

        // Shadow-mode position management. This persists HOLD/REDUCE/EXIT advice only.
        // It never changes the market signal and never executes a wallet transaction.
        if (positionManagementService != null) {
            try {
                positionManagementService.analyze(signal);
            } catch (RuntimeException ex) {
                log.warn("Position advisory failed for signal {}: {}", signal.getId(), ex.getMessage());
            }
        }
        Optional<PaperPosition> openPosition = positionRepository
                .findBySymbolAndStatus(symbol, PositionStatus.OPEN);

        if (openPosition.isPresent()) {
            PaperPosition position = openPosition.get();
            BigDecimal price = signal.getLatestPrice();

            // Never treat a delayed timeframe candle close as the current mark price.
            // Context from that signal is still processed below, but TP/SL/profit-lock
            // mechanics require a post-entry market observation. Live production protection
            // is independently driven by LivePositionProtectionService.onPrice(...).
            boolean authoritativePrice = priceAuthorityPolicy.canUseSignalPrice(signal, position.getOpenedAt());
            DynamicProfitLockService.Evaluation profitLock = authoritativePrice
                    ? dynamicProfitLockService.evaluate(signal)
                    : DynamicProfitLockService.Evaluation.inactive(
                            "Signal price predates the open position and is context-only for mechanical protection.");

            if (authoritativePrice && price.compareTo(position.getTakeProfit()) >= 0) {
                return Optional.of(closeFromSignal(position, signal, PositionStatus.CLOSED,
                        "TAKE_PROFIT", "Price reached the configured take-profit target."));
            }

            if (authoritativePrice && profitLock.triggered()) {
                return Optional.of(closeFromProfitLock(position, signal, profitLock));
            }

            if (authoritativePrice && price.compareTo(position.getStopLoss()) <= 0) {
                return Optional.of(closeFromSignal(position, signal, PositionStatus.STOPPED,
                        "STOP_LOSS", "Price reached the configured stop loss."));
            }

            if (signal.getDecision() == SignalDecision.SELL
                    || signal.getDecision() == SignalDecision.STRONG_SELL) {
                if (profitLock.active()) {
                    log.info("Normal wallet SELL suppressed because Dynamic Profit Lock is active: signalId={}, symbol={}, protectedPrice={}, currentPrice={}",
                            signal.getId(), signal.getSymbol(), profitLock.lockPrice(), signal.getLatestPrice());
                    return Optional.of(position);
                }
                TradeExecutionValidationService.ValidationResult validation =
                        executionValidationService.validateSell(signal);
                if (validation.allowed()) {
                    return Optional.of(closeFromSignal(position, signal, PositionStatus.CLOSED,
                            signal.getDecision().name(), signal.getExplanation()));
                }
                log.info("Normal wallet SELL rejected: signalId={}, symbol={}, interval={}, reason={}, detail={}",
                        signal.getId(), signal.getSymbol(), signal.getInterval(),
                        validation.code(), validation.explanation());
            }

            // Progressive Position Building may add to an existing position only after
            // TP / Profit Lock / SL / validated SELL logic had first priority.
            if ("1m".equals(signal.getInterval()) && !profitLock.active()) {
                WalletManagedPosition managed = walletManagedPositionRepository
                        .findTopBySymbolAndStatusOrderByOpenedAtDesc(symbol, "OPEN")
                        .orElse(null);
                if (managed != null) {
                    ExecutionIntelligenceService.ExecutionDecision addDecision =
                            executionIntelligenceService.evaluateBuy(
                                    signal,
                                    managed.getAllocatedPositionPercent(),
                                    managed.getEntryStage());
                    if (addDecision.allowed()) {
                        if (walletTradeRepository
                                .findTopBySignalIdAndSideAndStatusOrderByExecutedAtDesc(
                                        signal.getId(), "BUY", "EXECUTED")
                                .isPresent()) {
                            return Optional.of(position);
                        }
                        int addPercent = addDecision.positionPercent();
                        ExecutionIntelligenceService.EntryQuality entryQuality =
                                executionIntelligenceService.assessEntryQuality(signal);
                        boolean walletAdded = walletAutoExecutionService.executeBuy(
                                signal,
                                addPercent,
                                "Execution Intelligence [" + addDecision.source() + "] " + addDecision.explanation(),
                                addDecision.source(),
                                entryQuality.score());
                        if (walletAdded) {
                            BigDecimal addedQuantity = walletTradeRepository
                                    .findTopBySignalIdAndSideAndStatusOrderByExecutedAtDesc(
                                            signal.getId(), "BUY", "EXECUTED")
                                    .map(com.crypto.wallet.domain.WalletTrade::getQuantity)
                                    .filter(q -> q != null && q.signum() > 0)
                                    .orElse(BigDecimal.ZERO);
                            if (addedQuantity.signum() > 0) {
                                BigDecimal oldCost = position.getEntryPrice().multiply(position.getQuantity(), MC);
                                BigDecimal addCost = signal.getLatestPrice().multiply(addedQuantity, MC);
                                BigDecimal newQuantity = position.getQuantity().add(addedQuantity);
                                position.setQuantity(newQuantity);
                                position.setEntryPrice(oldCost.add(addCost, MC).divide(newQuantity, MC));
                                if (signal.getStopLoss() != null
                                        && signal.getStopLoss().compareTo(position.getStopLoss()) > 0) {
                                    position.setStopLoss(signal.getStopLoss());
                                }
                                if (signal.getTakeProfit() != null
                                        && signal.getTakeProfit().compareTo(position.getTakeProfit()) > 0) {
                                    position.setTakeProfit(signal.getTakeProfit());
                                }
                                position.setEntryReason(position.getEntryReason()
                                        + " | Progressive add [" + addDecision.source() + "] "
                                        + addPercent + "% at " + signal.getLatestPrice()
                                        + "; Entry Quality=" + entryQuality.score() + "/100.");
                                positionRepository.save(position);
                            }
                            executionIntelligenceService.markExecuted(signal, addDecision);
                        }
                    }
                }
            }

            // Non-execution timeframes and non-executable decisions only update context/advisory state.
            return Optional.of(position);
        }

        ExecutionIntelligenceService.ExecutionDecision executionDecision =
                executionIntelligenceService.evaluateBuy(signal);
        if (!executionDecision.allowed()) {
            log.info("Execution Intelligence did not open a BUY: signalId={}, symbol={}, interval={}, state={}, source={}, code={}, evidenceScore={}, buys={}, watches={}, detail={}",
                    signal.getId(), signal.getSymbol(), signal.getInterval(), executionDecision.state(),
                    executionDecision.source(), executionDecision.code(),
                    executionDecision.evidence().evidenceScore(), executionDecision.evidence().buyCount(),
                    executionDecision.evidence().watchCount(), executionDecision.explanation());
            return Optional.empty();
        }

        if (positionRepository.countByStatus(PositionStatus.OPEN) >= properties.maxOpenPositions()) {
            return Optional.empty();
        }

        enforceDailyLossLimit();

        int atrPercent = signal.getAtrRecommendedPositionPercent() <= 0
                ? 100
                : signal.getAtrRecommendedPositionPercent();
        int executionPercent = executionDecision.positionPercent();
        int effectivePositionPercent = Math.max(1,
                Math.min(100, (int) Math.round(atrPercent * executionPercent / 100.0)));
        BigDecimal positionScale = BigDecimal.valueOf(effectivePositionPercent)
                .divide(BigDecimal.valueOf(100), MC);
        BigDecimal riskAmount = properties.paperAccountBalance()
                .multiply(properties.riskPerTradePercent(), MC)
                .divide(BigDecimal.valueOf(100), MC)
                .multiply(positionScale, MC);
        BigDecimal riskPerUnit = signal.getLatestPrice().subtract(signal.getStopLoss(), MC).abs();

        if (riskPerUnit.signum() == 0) {
            throw new IllegalStateException("Invalid stop-loss distance");
        }

        BigDecimal riskModelQuantity = riskAmount.divide(riskPerUnit, MC);

        final boolean walletExecuted;
        try {
            ExecutionIntelligenceService.EntryQuality entryQuality =
                    executionIntelligenceService.assessEntryQuality(signal);
            walletExecuted = walletAutoExecutionService.executeBuy(signal, effectivePositionPercent,
                    "Execution Intelligence [" + executionDecision.source() + "] " + executionDecision.explanation(),
                    executionDecision.source(),
                    entryQuality.score());
        } catch (RuntimeException ex) {
            log.error("Automatic wallet BUY failed for signal {}: {}", signal.getId(), ex.getMessage(), ex);
            return Optional.empty();
        }
        if (!walletExecuted) {
            log.info("Execution Intelligence approved signal {} but wallet controls declined execution; no paper position was created.", signal.getId());
            return Optional.empty();
        }

        BigDecimal executedQuantity = walletTradeRepository
                .findTopBySignalIdAndSideAndStatusOrderByExecutedAtDesc(signal.getId(), "BUY", "EXECUTED")
                .map(com.crypto.wallet.domain.WalletTrade::getQuantity)
                .filter(q -> q != null && q.signum() > 0)
                .orElse(riskModelQuantity);

        executionIntelligenceService.markExecuted(signal, executionDecision);
        PaperPosition position = positionRepository.save(PaperPosition.builder()
                .symbol(symbol)
                .side(PositionSide.BUY)
                .status(PositionStatus.OPEN)
                .quantity(executedQuantity)
                .entryPrice(signal.getLatestPrice())
                .stopLoss(signal.getStopLoss())
                .takeProfit(signal.getTakeProfit())
                .signal(signal)
                .entryReason(signal.getExplanation() + " | Execution Intelligence [" + executionDecision.source() + "]: " + executionDecision.explanation() + " Effective position " + effectivePositionPercent + "%.")
                .openedAt(Instant.now())
                .build());
        return Optional.of(position);
    }

    /** Kept for compatibility with older callers. */
    @Transactional
    public Optional<PaperPosition> openFromSignal(TradeSignal signal) {
        return processSignal(signal);
    }

    @Transactional
    public PaperPosition close(Long positionId, BigDecimal exitPrice) {
        PaperPosition position = positionRepository.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("Position not found"));

        if (position.getStatus() != PositionStatus.OPEN) {
            throw new IllegalStateException("Position is already closed");
        }

        return completeClose(position, exitPrice,
                exitPrice.compareTo(position.getStopLoss()) <= 0 ? PositionStatus.STOPPED : PositionStatus.CLOSED,
                "MANUAL_CLOSE", "Position was manually closed.", null);
    }

    @Transactional(readOnly = true)
    public List<PaperPosition> list() {
        return positionRepository.findTop100ByOrderByOpenedAtDesc();
    }

    private PaperPosition closeFromSignal(
            PaperPosition position,
            TradeSignal signal,
            PositionStatus status,
            String closeReason,
            String explanation
    ) {
        return completeClose(position, signal.getLatestPrice(), status, closeReason, explanation, signal);
    }

    private PaperPosition closeFromProfitLock(
            PaperPosition position,
            TradeSignal signal,
            DynamicProfitLockService.Evaluation profitLock
    ) {
        BigDecimal exitPrice = signal.getLatestPrice();
        BigDecimal pnl = exitPrice.subtract(position.getEntryPrice(), MC)
                .multiply(position.getQuantity(), MC);

        position.setExitPrice(exitPrice);
        position.setRealizedPnl(pnl);
        position.setStatus(PositionStatus.CLOSED);
        position.setCloseReason("PROFIT_LOCK");
        position.setExitReason(profitLock.explanation());
        position.setExitSignal(signal);
        position.setClosedAt(Instant.now());
        PaperPosition saved = positionRepository.save(position);

        try {
            walletAutoExecutionService.executeProfitLock(signal, exitPrice, profitLock.lockPrice());
        } catch (RuntimeException ex) {
            log.error("Dynamic Profit Lock wallet exit failed for signal {}: {}", signal.getId(), ex.getMessage(), ex);
        }
        return saved;
    }

    private PaperPosition completeClose(
            PaperPosition position,
            BigDecimal exitPrice,
            PositionStatus status,
            String closeReason,
            String explanation,
            TradeSignal exitSignal
    ) {
        BigDecimal pnl = exitPrice.subtract(position.getEntryPrice(), MC)
                .multiply(position.getQuantity(), MC);

        position.setExitPrice(exitPrice);
        position.setRealizedPnl(pnl);
        position.setStatus(status);
        position.setCloseReason(closeReason);
        position.setExitReason(explanation);
        position.setExitSignal(exitSignal);
        position.setClosedAt(Instant.now());
        PaperPosition saved = positionRepository.save(position);
        if (exitSignal != null) {
            try {
                walletAutoExecutionService.executeSell(exitSignal);
            } catch (RuntimeException ex) {
                log.error("Automatic wallet SELL failed for signal {}: {}", exitSignal.getId(), ex.getMessage(), ex);
            }
        }
        return saved;
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        return symbol.trim().toUpperCase();
    }

    private void enforceDailyLossLimit() {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        BigDecimal pnl = positionRepository.sumRealizedPnlSince(startOfDay);
        BigDecimal maximumLoss = properties.paperAccountBalance()
                .multiply(properties.maxDailyLossPercent(), MC)
                .divide(BigDecimal.valueOf(100), MC)
                .negate();

        if (pnl.compareTo(maximumLoss) <= 0) {
            throw new IllegalStateException("Daily loss limit reached");
        }
    }
}
