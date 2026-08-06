package com.crypto.service;

import com.crypto.domain.Candle;
import com.crypto.domain.PaperPosition;
import com.crypto.domain.TradeSignal;
import com.crypto.dto.TradeReplayResponse;
import com.crypto.position.domain.PositionAnalysis;
import com.crypto.position.repository.PositionAnalysisRepository;
import com.crypto.repository.CandleRepository;
import com.crypto.repository.PaperPositionRepository;
import com.crypto.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradeReplayService {

    private static final Duration PRE_ENTRY_WINDOW = Duration.ofMinutes(5);
    private static final Duration POST_EXIT_WINDOW = Duration.ofHours(2);

    private final PaperPositionRepository positionRepository;
    private final TradeSignalRepository signalRepository;
    private final CandleRepository candleRepository;
    private final PositionAnalysisRepository positionAnalysisRepository;

    @Transactional(readOnly = true)
    public TradeReplayResponse replay(Long positionId) {
        PaperPosition position = positionRepository.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("Paper position not found: " + positionId));

        Instant lifecycleEnd = position.getClosedAt() == null ? Instant.now() : position.getClosedAt();
        Instant replayStart = position.getOpenedAt().minus(PRE_ENTRY_WINDOW);
        Instant replayEnd = lifecycleEnd.plus(position.getClosedAt() == null ? Duration.ZERO : POST_EXIT_WINDOW);

        List<TradeSignal> signals = signalRepository
                .findBySymbolAndGeneratedAtBetweenOrderByGeneratedAtAsc(position.getSymbol(), replayStart, replayEnd);

        String preferredInterval = position.getSignal() == null || position.getSignal().getInterval() == null
                ? "1m"
                : position.getSignal().getInterval();

        List<Candle> candles = candleRepository
                .findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeAsc(
                        position.getSymbol(), preferredInterval, replayStart, replayEnd);

        List<PositionAnalysis> advice = positionAnalysisRepository
                .findBySymbolAndAnalyzedAtBetweenOrderByAnalyzedAtAsc(
                        position.getSymbol(), position.getOpenedAt(), lifecycleEnd);

        return new TradeReplayResponse(
                positionSummary(position),
                signalSummary(position.getSignal()),
                signalSummary(position.getExitSignal()),
                signals.stream().map(this::signalSummary).toList(),
                candles.stream().map(this::candlePoint).toList(),
                advice.stream().map(this::positionAdvice).toList(),
                afterExit(position, candles)
        );
    }

    private TradeReplayResponse.PositionSummary positionSummary(PaperPosition position) {
        BigDecimal pnlPercent = null;
        if (position.getExitPrice() != null && position.getEntryPrice() != null
                && position.getEntryPrice().signum() != 0) {
            pnlPercent = position.getExitPrice().subtract(position.getEntryPrice())
                    .divide(position.getEntryPrice(), 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return new TradeReplayResponse.PositionSummary(
                position.getId(), position.getSymbol(), position.getStatus().name(), position.getQuantity(),
                position.getEntryPrice(), position.getExitPrice(), position.getStopLoss(), position.getTakeProfit(),
                position.getRealizedPnl(), pnlPercent, position.getCloseReason(), position.getEntryReason(),
                position.getExitReason(), position.getOpenedAt(), position.getClosedAt());
    }

    private TradeReplayResponse.SignalSummary signalSummary(TradeSignal signal) {
        if (signal == null) return null;
        return new TradeReplayResponse.SignalSummary(
                signal.getId(), signal.getInterval(), signal.getGeneratedAt(), signal.getLatestPrice(),
                signal.getOriginalDecision() == null ? null : signal.getOriginalDecision().name(),
                signal.getDecision() == null ? null : signal.getDecision().name(),
                signal.getTotalScore(), signal.getConfidenceScore(), signal.getTrendScore(), signal.getVolumeScore(),
                signal.getMomentumScore(), signal.getSentimentScore(), signal.getFundamentalScore(),
                signal.getConfluenceStatus() == null ? null : signal.getConfluenceStatus().name(),
                signal.getLiquidityStatus() == null ? null : signal.getLiquidityStatus().name(),
                signal.isFinalEntryAllowed(), signal.getFinalDecisionExplanation());
    }

    private TradeReplayResponse.CandlePoint candlePoint(Candle candle) {
        return new TradeReplayResponse.CandlePoint(candle.getOpenTime(), candle.getOpenPrice(), candle.getHighPrice(),
                candle.getLowPrice(), candle.getClosePrice(), candle.getVolume());
    }

    private TradeReplayResponse.PositionAdvice positionAdvice(PositionAnalysis analysis) {
        return new TradeReplayResponse.PositionAdvice(
                analysis.getId(), analysis.getTradeSignal().getId(), analysis.getIntervalCode(), analysis.getAnalyzedAt(),
                analysis.getCurrentPriceUsdt(), analysis.getUnrealizedPnlPercent(), analysis.getExitScore(),
                analysis.getRecommendation().name(), analysis.getConfidence(), analysis.getExplanation(),
                analysis.isAdvisoryOnly());
    }

    private TradeReplayResponse.AfterExitSummary afterExit(PaperPosition position, List<Candle> candles) {
        if (position.getClosedAt() == null || position.getExitPrice() == null) return null;

        List<Candle> postExit = candles.stream()
                .filter(c -> !c.getOpenTime().isBefore(position.getClosedAt()))
                .toList();
        if (postExit.isEmpty()) return null;

        BigDecimal high = postExit.stream().map(Candle::getHighPrice).max(Comparator.naturalOrder()).orElse(null);
        BigDecimal low = postExit.stream().map(Candle::getLowPrice).min(Comparator.naturalOrder()).orElse(null);
        BigDecimal highMove = percentMove(position.getExitPrice(), high);
        BigDecimal lowMove = percentMove(position.getExitPrice(), low);
        int minutes = (int) Math.min(120, Math.max(1,
                Duration.between(position.getClosedAt(), postExit.get(postExit.size() - 1).getCloseTime()).toMinutes()));

        String verdict;
        if (highMove != null && highMove.compareTo(BigDecimal.valueOf(0.75)) >= 0) {
            verdict = "Price recovered materially after exit; review entry timing or stop distance.";
        } else if (lowMove != null && lowMove.compareTo(BigDecimal.valueOf(-0.75)) <= 0) {
            verdict = "Price continued lower after exit; the exit protected capital.";
        } else {
            verdict = "Post-exit movement was limited; the exit was broadly neutral.";
        }
        return new TradeReplayResponse.AfterExitSummary(minutes, high, low, highMove, lowMove, verdict);
    }

    private BigDecimal percentMove(BigDecimal from, BigDecimal to) {
        if (from == null || to == null || from.signum() == 0) return null;
        return to.subtract(from).divide(from, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }
}
