package com.crypto.service;

import com.crypto.config.TradingProperties;
import com.crypto.domain.PaperPosition;
import com.crypto.domain.PositionSide;
import com.crypto.domain.PositionStatus;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.repository.PaperPositionRepository;
import com.crypto.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
public class PaperTradingService {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final TradingProperties properties;
    private final TradeSignalRepository signalRepository;
    private final PaperPositionRepository positionRepository;

    /**
     * Manual entry point used by the controller.
     */
    @Transactional
    public PaperPosition openFromLatestSignal(String symbol) {
        String normalized = normalizeSymbol(symbol);

        TradeSignal signal = signalRepository
                .findTopBySymbolOrderByGeneratedAtDesc(normalized)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No signal found for " + normalized
                ));

        return openFromSignal(signal)
                .orElseThrow(() -> new IllegalStateException(
                        "Latest signal is not eligible for a paper trade " +
                                "or an open-position limit prevents it"
                ));
    }

    /**
     * Automatic entry point. It uses the exact TradeSignal just created by
     * AnalysisService, avoiding a second latest-signal lookup or race condition.
     */
    @Transactional
    public Optional<PaperPosition> openFromSignal(TradeSignal signal) {
        if (signal == null) {
            throw new IllegalArgumentException("Trade signal is required");
        }

        String symbol = normalizeSymbol(signal.getSymbol());

        if (!isEligible(signal)) {
            return Optional.empty();
        }

        if (positionRepository.countByStatus(PositionStatus.OPEN)
                >= properties.maxOpenPositions()) {
            return Optional.empty();
        }

        if (positionRepository.existsBySymbolAndStatus(symbol, PositionStatus.OPEN)) {
            return Optional.empty();
        }

        enforceDailyLossLimit();

        BigDecimal riskAmount = properties.paperAccountBalance()
                .multiply(properties.riskPerTradePercent(), MC)
                .divide(BigDecimal.valueOf(100), MC);

        BigDecimal riskPerUnit = signal.getLatestPrice()
                .subtract(signal.getStopLoss(), MC)
                .abs();

        if (riskPerUnit.signum() == 0) {
            throw new IllegalStateException("Invalid stop-loss distance");
        }

        BigDecimal quantity = riskAmount.divide(riskPerUnit, MC);

        PaperPosition position = positionRepository.save(PaperPosition.builder()
                .symbol(symbol)
                .side(PositionSide.BUY)
                .status(PositionStatus.OPEN)
                .quantity(quantity)
                .entryPrice(signal.getLatestPrice())
                .stopLoss(signal.getStopLoss())
                .takeProfit(signal.getTakeProfit())
                .signal(signal)
                .openedAt(Instant.now())
                .build());

        return Optional.of(position);
    }

    @Transactional
    public PaperPosition close(Long positionId, BigDecimal exitPrice) {
        PaperPosition position = positionRepository.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("Position not found"));

        if (position.getStatus() != PositionStatus.OPEN) {
            throw new IllegalStateException("Position is already closed");
        }

        BigDecimal pnl = exitPrice.subtract(position.getEntryPrice(), MC)
                .multiply(position.getQuantity(), MC);

        position.setExitPrice(exitPrice);
        position.setRealizedPnl(pnl);
        position.setStatus(exitPrice.compareTo(position.getStopLoss()) <= 0
                ? PositionStatus.STOPPED
                : PositionStatus.CLOSED);
        position.setClosedAt(Instant.now());
        return positionRepository.save(position);
    }

    @Transactional(readOnly = true)
    public List<PaperPosition> list() {
        return positionRepository.findTop100ByOrderByOpenedAtDesc();
    }

    private boolean isEligible(TradeSignal signal) {
        return signal.getTotalScore() >= properties.minimumBuyScore()
                && (signal.getDecision() == SignalDecision.BUY
                || signal.getDecision() == SignalDecision.STRONG_BUY);
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        return symbol.trim().toUpperCase();
    }

    private void enforceDailyLossLimit() {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);

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
