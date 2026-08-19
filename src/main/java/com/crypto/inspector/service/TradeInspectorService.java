package com.crypto.inspector.service;

import com.crypto.domain.Candle;
import com.crypto.domain.TradeSignal;
import com.crypto.dto.TradeInspectorResponse;
import com.crypto.dto.TradeInspectorSummary;
import com.crypto.dto.TradeInspectorTradeView;
import com.crypto.repository.CandleRepository;
import com.crypto.repository.PaperPositionRepository;
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

    public TradeInspectorService(WalletTradeRepository walletTradeRepository,
                                 CandleRepository candleRepository,
                                 PaperPositionRepository paperPositionRepository,
                                 WalletManagedPositionRepository walletManagedPositionRepository) {
        this.walletTradeRepository = walletTradeRepository;
        this.candleRepository = candleRepository;
        this.paperPositionRepository = paperPositionRepository;
        this.walletManagedPositionRepository = walletManagedPositionRepository;
    }

    @Transactional(readOnly = true)
    public TradeInspectorResponse inspect(String requestedSymbol, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        String symbol = normalizeSymbol(requestedSymbol);

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
        Long tradeHistoryId = null;
        if (entry != null && exit != null) {
            tradeHistoryId = paperPositionRepository.findBySignalPair(entry.getId(), exit.getId()).stream()
                    .findFirst().map(p -> p.getId()).orElse(null);
        }

        WalletManagedPosition managed = entry == null ? null : walletManagedPositionRepository
                .findTopByEntrySignalIdOrderByOpenedAtDesc(entry.getId()).orElse(null);

        return new TradeInspectorTradeView(
                buy.getId(), sell.getId(), tradeHistoryId, sell.getSymbol(), openedAt, closedAt,
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
                humanCloseReason(sell),
                bestPrice, percentChange(entryPrice, bestPrice),
                worstPrice, percentChange(entryPrice, worstPrice),
                p15, p30, p60, assessment.quality(), assessment.explanation()
        );
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
         * FIX-007 / Trade Inspector navigation:
         * - When no range is supplied, return the COMPLETE closed-candle history for
         *   the selected symbol + interval. The browser renders the full series but
         *   initially zooms around the inspected BUY/SELL, so panning left/right never
         *   hits the old fixed +/-7-day wall.
         * - A bounded range is still supported for backward compatibility.
         * - Never synthesize/fill missing candles. Historical gaps remain visible so
         *   the chart cannot imply market data that was never persisted.
         * - This endpoint is read-only and does not touch trading/replay state.
         */
        List<Candle> candles = from == null
                ? candleRepository.findBySymbolAndIntervalCodeAndClosedTrueOrderByOpenTimeAsc(symbol, interval)
                : candleRepository.findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeAsc(symbol, interval, from, to)
                    .stream().filter(Candle::isClosed).toList();

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
        response.put("fullHistory", from == null);
        response.put("pointCount", rows.size());
        response.put("firstOpenTime", candles.isEmpty() ? null : candles.getFirst().getOpenTime());
        response.put("lastOpenTime", candles.isEmpty() ? null : candles.getLast().getOpenTime());
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
}
