package com.crypto.wallet.service;

import com.crypto.domain.Candle;
import com.crypto.domain.TradeSignal;
import com.crypto.repository.CandleRepository;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.wallet.domain.*;
import com.crypto.wallet.dto.*;
import com.crypto.wallet.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WalletService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int SCALE = 12;
    private final WalletAssetRepository assetRepository;
    private final WalletTradeRepository tradeRepository;
    private final WalletCashFlowRepository cashFlowRepository;
    private final WalletSnapshotRepository snapshotRepository;
    private final TradeSignalRepository signalRepository;
    private final CandleRepository candleRepository;
    private final WalletSettingsRepository settingsRepository;
    private final WalletDailyStatisticsRepository dailyStatisticsRepository;

    @Transactional(readOnly = true)
    public Map<String,Object> overview() {
        List<WalletAsset> assets = assetRepository.findAllByOrderBySymbolAsc();
        BigDecimal portfolio = ZERO;
        BigDecimal unrealized = ZERO;
        List<Map<String,Object>> rows = new ArrayList<>();
        for (WalletAsset asset : assets) {
            BigDecimal price = currentPrice(asset.getSymbol());
            BigDecimal value = asset.getQuantity().multiply(price);
            BigDecimal cost = "USDT".equals(asset.getSymbol()) ? value : asset.getQuantity().multiply(nvl(asset.getAverageBuyPriceUsdt()));
            BigDecimal pnl = "USDT".equals(asset.getSymbol()) ? ZERO : value.subtract(cost);
            portfolio = portfolio.add(value);
            unrealized = unrealized.add(pnl);
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id", asset.getId()); row.put("symbol", asset.getSymbol()); row.put("quantity", asset.getQuantity());
            row.put("averageBuyPriceUsdt", asset.getAverageBuyPriceUsdt()); row.put("currentPriceUsdt", price);
            row.put("costBasisUsdt", cost); row.put("currentValueUsdt", value); row.put("unrealizedPnlUsdt", pnl);
            row.put("unrealizedPnlPercent", percent(pnl, cost)); rows.add(row);
        }
        BigDecimal netInvested = netInvested();
        BigDecimal realized = nvl(tradeRepository.totalRealizedPnl());
        BigDecimal totalPnl = portfolio.subtract(netInvested);
        BigDecimal available = assetRepository.findBySymbol("USDT").map(WalletAsset::getQuantity).orElse(ZERO);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("portfolioValueUsdt", portfolio); result.put("netInvestedUsdt", netInvested);
        result.put("totalPnlUsdt", totalPnl); result.put("totalReturnPercent", percent(totalPnl, netInvested));
        result.put("realizedPnlUsdt", realized); result.put("unrealizedPnlUsdt", unrealized);
        result.put("availableUsdt", available);
        WalletSettings settings = settingsRepository.findById(1L).orElse(null);
        result.put("settings", settings);
        result.put("dailyTrading", dailyTradingSummary(settings, available, portfolio));
        result.put("tradePerformance", tradePerformanceSummary(settings));
        result.put("portfolioStatus", totalPnl.signum() >= 0 ? "WINNING" : "LOSING");
        List<WalletSnapshot> snapshots = snapshotRepository.findTop200ByOrderByCapturedAtDesc();
        BigDecimal finalPortfolioValue = portfolio;
        BigDecimal change24h = snapshots.stream()
                .filter(x -> x.getCapturedAt().isAfter(Instant.now().minusSeconds(86400)))
                .min(Comparator.comparing(WalletSnapshot::getCapturedAt))
                .map(x -> finalPortfolioValue.subtract(x.getPortfolioValueUsdt()))
                .orElse(ZERO);
        result.put("change24hUsdt", change24h);
        result.put("assets", rows);
        result.put("trades", tradeRepository.findTop100ByOrderByExecutedAtDesc().stream().map(this::tradeDto).toList());
        result.put("snapshots", snapshots.stream().sorted(Comparator.comparing(WalletSnapshot::getCapturedAt)).toList());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String,Object> dashboardOverview() {
        List<WalletAsset> assets = assetRepository.findAllByOrderBySymbolAsc();
        BigDecimal portfolio = ZERO;
        BigDecimal unrealized = ZERO;
        List<Map<String,Object>> rows = new ArrayList<>();
        for (WalletAsset asset : assets) {
            BigDecimal price = currentPrice(asset.getSymbol());
            BigDecimal value = asset.getQuantity().multiply(price);
            BigDecimal cost = "USDT".equals(asset.getSymbol())
                    ? value
                    : asset.getQuantity().multiply(nvl(asset.getAverageBuyPriceUsdt()));
            BigDecimal pnl = "USDT".equals(asset.getSymbol()) ? ZERO : value.subtract(cost);
            portfolio = portfolio.add(value);
            unrealized = unrealized.add(pnl);

            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id", asset.getId());
            row.put("symbol", asset.getSymbol());
            row.put("quantity", asset.getQuantity());
            row.put("averageBuyPriceUsdt", asset.getAverageBuyPriceUsdt());
            row.put("currentPriceUsdt", price);
            row.put("costBasisUsdt", cost);
            row.put("currentValueUsdt", value);
            row.put("unrealizedPnlUsdt", pnl);
            row.put("unrealizedPnlPercent", percent(pnl, cost));
            rows.add(row);
        }

        BigDecimal netInvested = netInvested();
        BigDecimal realized = nvl(tradeRepository.totalRealizedPnl());
        BigDecimal totalPnl = portfolio.subtract(netInvested);
        BigDecimal available = assetRepository.findBySymbol("USDT")
                .map(WalletAsset::getQuantity)
                .orElse(ZERO);
        WalletSettings settings = settingsRepository.findById(1L).orElse(null);

        BigDecimal finalPortfolioValue = portfolio;
        BigDecimal change24h = snapshotRepository
                .findFirstByCapturedAtGreaterThanEqualOrderByCapturedAtAsc(Instant.now().minusSeconds(86400))
                .map(snapshot -> finalPortfolioValue.subtract(snapshot.getPortfolioValueUsdt()))
                .orElse(ZERO);

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("portfolioValueUsdt", portfolio);
        result.put("netInvestedUsdt", netInvested);
        result.put("totalPnlUsdt", totalPnl);
        result.put("totalReturnPercent", percent(totalPnl, netInvested));
        result.put("realizedPnlUsdt", realized);
        result.put("unrealizedPnlUsdt", unrealized);
        result.put("availableUsdt", available);
        result.put("settings", settings);
        result.put("dailyTrading", dailyTradingSummary(settings, available, portfolio));
        result.put("tradePerformance", tradePerformanceSummary(settings));
        result.put("portfolioStatus", totalPnl.signum() >= 0 ? "WINNING" : "LOSING");
        result.put("change24hUsdt", change24h);
        result.put("assets", rows);
        return result;
    }

    @Transactional
    public void setAsset(WalletAssetRequest request) {
        String symbol = normalizeAsset(request.symbol());
        WalletAsset asset = assetRepository.findBySymbol(symbol).orElseGet(() -> WalletAsset.builder().symbol(symbol).enabled(true).build());
        asset.setQuantity(nonNegative(request.quantity(), "Quantity"));
        asset.setAverageBuyPriceUsdt("USDT".equals(symbol) ? BigDecimal.ONE : nonNegativeNullable(request.averageBuyPriceUsdt(), "Average price"));
        assetRepository.save(asset); captureSnapshot();
    }

    @Transactional
    public void addCashFlow(WalletCashFlowRequest request) {
        String type = requireOne(request.flowType(), Set.of("DEPOSIT","WITHDRAWAL"), "flow type");
        BigDecimal amount = positive(request.amountUsdt(), "Amount");
        WalletAsset usdt = getOrCreate("USDT");
        if ("WITHDRAWAL".equals(type) && usdt.getQuantity().compareTo(amount) < 0) throw new IllegalArgumentException("Insufficient USDT balance");
        usdt.setQuantity("DEPOSIT".equals(type) ? usdt.getQuantity().add(amount) : usdt.getQuantity().subtract(amount));
        assetRepository.save(usdt);
        cashFlowRepository.save(WalletCashFlow.builder().flowType(type).amountUsdt(amount).occurredAt(Instant.now()).notes(request.notes()).build());
        captureSnapshot();
    }

    @Transactional
    public void updateSettings(WalletSettingsRequest request) {
        if (request.minimumUsdtReserve() == null || request.minimumUsdtReserve().signum() < 0)
            throw new IllegalArgumentException("Minimum reserve cannot be negative");
        if (request.baseTradeAmountUsdt() == null || request.baseTradeAmountUsdt().signum() <= 0)
            throw new IllegalArgumentException("Trade amount per BUY must be greater than zero");
        if (request.maximumDailyNewPositions() == null
                || request.maximumDailyNewPositions() < 0
                || request.maximumDailyNewPositions() > 1000)
            throw new IllegalArgumentException("Maximum daily new positions must be 0 (unlimited) or between 1 and 1000");

        BigDecimal profitLockActivationPercent = request.profitLockActivationPercent() == null
                ? BigDecimal.valueOf(70) : request.profitLockActivationPercent();
        BigDecimal profitLockInitialPercent = request.profitLockInitialPercent() == null
                ? BigDecimal.valueOf(40) : request.profitLockInitialPercent();
        BigDecimal profitLockTrailStepPercent = request.profitLockTrailStepPercent() == null
                ? BigDecimal.valueOf(10) : request.profitLockTrailStepPercent();
        if (profitLockActivationPercent.compareTo(BigDecimal.valueOf(1)) < 0
                || profitLockActivationPercent.compareTo(BigDecimal.valueOf(99)) > 0)
            throw new IllegalArgumentException("Profit lock activation must be between 1% and 99% of the take-profit distance");
        if (profitLockInitialPercent.signum() < 0
                || profitLockInitialPercent.compareTo(profitLockActivationPercent) >= 0)
            throw new IllegalArgumentException("Initial locked profit must be >= 0% and below the activation percentage");
        if (profitLockTrailStepPercent.compareTo(BigDecimal.ONE) < 0
                || profitLockTrailStepPercent.compareTo(BigDecimal.valueOf(50)) > 0)
            throw new IllegalArgumentException("Profit lock trail step must be between 1% and 50%");

        String performanceWindowType = normalizePerformanceWindowType(request.performanceWindowType());
        String dashboardIntervals = normalizeDashboardIntervals(request.dashboardIntervals());
        String executionProfile = normalizeExecutionProfile(request.executionProfile());
        int performanceTradeCount = Optional.ofNullable(request.performanceTradeCount()).orElse(20);
        int performancePeriodDays = Optional.ofNullable(request.performancePeriodDays()).orElse(1);
        if (performanceTradeCount < 1 || performanceTradeCount > 500)
            throw new IllegalArgumentException("Performance trade count must be between 1 and 500");
        if (performancePeriodDays < 1 || performancePeriodDays > 3650)
            throw new IllegalArgumentException("Performance period must be between 1 and 3650 days");
        if ("DATE_RANGE".equals(performanceWindowType)) {
            if (request.performanceStartDate() == null || request.performanceEndDate() == null)
                throw new IllegalArgumentException("Performance start and end dates are required for a date range");
            if (request.performanceEndDate().isBefore(request.performanceStartDate()))
                throw new IllegalArgumentException("Performance end date cannot be before the start date");
        }

        WalletSettings settings = settingsRepository.findById(1L).orElseGet(() -> WalletSettings.builder()
                .id(1L)
                .baseTradeAmountUsdt(BigDecimal.valueOf(100))
                .performanceWindowType("LAST_TRADES")
                .performanceTradeCount(20)
                .performancePeriodDays(1)
                .dashboardIntervals("1m,5m,1h,4h,1d")
                .requireNewBuyTransition(true)
                .executionProfile("BALANCED")
                .dynamicProfitLockEnabled(true)
                .profitLockActivationPercent(BigDecimal.valueOf(70))
                .profitLockInitialPercent(BigDecimal.valueOf(40))
                .profitLockTrailStepPercent(BigDecimal.valueOf(10))
                .build());
        settings.setMinimumUsdtReserve(request.minimumUsdtReserve());
        settings.setBaseTradeAmountUsdt(request.baseTradeAmountUsdt());
        settings.setMaximumDailyNewPositions(request.maximumDailyNewPositions());
        settings.setPerformanceWindowType(performanceWindowType);
        settings.setPerformanceTradeCount(performanceTradeCount);
        settings.setPerformancePeriodDays(performancePeriodDays);
        settings.setPerformanceStartDate(request.performanceStartDate());
        settings.setPerformanceEndDate(request.performanceEndDate());
        settings.setDashboardIntervals(dashboardIntervals);
        settings.setRequireNewBuyTransition(request.requireNewBuyTransition() == null || request.requireNewBuyTransition());
        settings.setExecutionProfile(executionProfile);
        settings.setDynamicProfitLockEnabled(request.dynamicProfitLockEnabled() == null || request.dynamicProfitLockEnabled());
        settings.setProfitLockActivationPercent(profitLockActivationPercent);
        settings.setProfitLockInitialPercent(profitLockInitialPercent);
        settings.setProfitLockTrailStepPercent(profitLockTrailStepPercent);
        settings.setUpdatedAt(Instant.now());
        settingsRepository.save(settings);

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        dailyStatisticsRepository.findForUpdateByTradeDate(today).ifPresent(statistics -> {
            BigDecimal available = assetRepository.findBySymbol("USDT")
                    .map(WalletAsset::getQuantity)
                    .orElse(ZERO);
            BigDecimal tradable = available.subtract(settings.getMinimumUsdtReserve()).max(ZERO);
            int maximum = settings.getMaximumDailyNewPositions();
            BigDecimal budget = maximum == 0
                    ? settings.getBaseTradeAmountUsdt().min(tradable)
                    : (tradable.signum() <= 0 ? ZERO
                    : tradable.divide(BigDecimal.valueOf(maximum), SCALE, RoundingMode.DOWN));
            statistics.setMaximumNewPositions(maximum);
            statistics.setDailyTradeBudgetUsdt(budget);
            statistics.setUpdatedAt(Instant.now());
            dailyStatisticsRepository.save(statistics);
        });
    }


    private String normalizeExecutionProfile(String value) {
        if (value == null || value.isBlank()) return "BALANCED";
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("CONSERVATIVE", "BALANCED", "AGGRESSIVE").contains(normalized)) {
            throw new IllegalArgumentException("Execution profile must be CONSERVATIVE, BALANCED, or AGGRESSIVE");
        }
        return normalized;
    }

    private String normalizeDashboardIntervals(String value) {
        List<String> allowed = List.of("1m", "5m", "1h", "4h", "1d");
        if (value == null || value.isBlank()) return String.join(",", allowed);
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            String normalized = item.trim().toLowerCase(Locale.ROOT);
            if (allowed.contains(normalized)) selected.add(normalized);
        }
        // Core trading intervals remain visible by default if an invalid/empty value is submitted.
        if (selected.isEmpty()) selected.addAll(List.of("1m", "5m", "1h"));
        return String.join(",", selected);
    }


    @Transactional(readOnly = true)
    public Map<String, Object> tradePerformanceSummary(WalletSettings settings) {
        WalletSettings effective = settings != null ? settings : settingsRepository.findById(1L).orElse(null);
        String type = effective == null ? "LAST_TRADES" : normalizePerformanceWindowType(effective.getPerformanceWindowType());
        int tradeCount = effective == null || effective.getPerformanceTradeCount() <= 0 ? 20 : effective.getPerformanceTradeCount();
        int periodDays = effective == null || effective.getPerformancePeriodDays() <= 0 ? 1 : effective.getPerformancePeriodDays();
        ZoneId zone = ZoneId.systemDefault();
        Instant now = Instant.now();

        if ("TODAY".equals(type)) {
            LocalDate today = LocalDate.now(zone);
            Instant from = today.atStartOfDay(zone).toInstant();
            Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();
            return performanceFromAggregate(type, "Today",
                    tradeRepository.summarizeClosedTradesBetween(from, to),
                    tradeRepository.summarizeClosedTradePnlBySymbolBetween(from, to));
        }

        if ("LAST_DAYS".equals(type)) {
            Instant from = ZonedDateTime.now(zone).minusDays(periodDays).toInstant();
            Instant to = now.plusNanos(1);
            return performanceFromAggregate(type, "Last " + periodDays + (periodDays == 1 ? " day" : " days"),
                    tradeRepository.summarizeClosedTradesBetween(from, to),
                    tradeRepository.summarizeClosedTradePnlBySymbolBetween(from, to));
        }

        if ("DATE_RANGE".equals(type)) {
            LocalDate start = effective == null ? null : effective.getPerformanceStartDate();
            LocalDate end = effective == null ? null : effective.getPerformanceEndDate();
            if (start != null && end != null && !end.isBefore(start)) {
                Instant from = start.atStartOfDay(zone).toInstant();
                Instant to = end.plusDays(1).atStartOfDay(zone).toInstant();
                return performanceFromAggregate(type, start + " to " + end,
                        tradeRepository.summarizeClosedTradesBetween(from, to),
                        tradeRepository.summarizeClosedTradePnlBySymbolBetween(from, to));
            }
        }

        List<WalletTrade> trades = tradeRepository.findRecentClosedTrades(PageRequest.of(0, tradeCount));
        return performanceFromTrades("LAST_TRADES", "Last " + tradeCount + " trades", trades);
    }

    private Map<String, Object> performanceFromTrades(String type, String label, List<WalletTrade> trades) {
        long wins = 0;
        long losses = 0;
        long breakeven = 0;
        BigDecimal netPnl = ZERO;
        BigDecimal grossProfit = ZERO;
        BigDecimal grossLoss = ZERO;
        Map<String, CoinPerformanceAccumulator> bySymbol = new LinkedHashMap<>();
        for (WalletTrade trade : trades) {
            BigDecimal pnl = nvl(trade.getRealizedPnlUsdt());
            netPnl = netPnl.add(pnl);
            if (pnl.signum() > 0) {
                wins++;
                grossProfit = grossProfit.add(pnl);
            } else if (pnl.signum() < 0) {
                losses++;
                grossLoss = grossLoss.add(pnl.abs());
            } else {
                breakeven++;
            }
            bySymbol.computeIfAbsent(trade.getSymbol(), ignored -> new CoinPerformanceAccumulator())
                    .add(pnl);
        }
        Map<String, Object> result = buildPerformanceSummary(type, label, trades.size(), wins, losses, breakeven, netPnl, grossProfit, grossLoss);
        attachCoinLeaders(result, bySymbol.entrySet().stream()
                .map(entry -> new CoinPerformance(entry.getKey(), entry.getValue().netPnl(), entry.getValue().tradeCount()))
                .toList());
        return result;
    }

    private Map<String, Object> performanceFromAggregate(
            String type,
            String label,
            Object[] aggregate,
            List<Object[]> bySymbolRows) {
        long count = numberAsLong(aggregate, 0);
        long wins = numberAsLong(aggregate, 1);
        long losses = numberAsLong(aggregate, 2);
        long breakeven = numberAsLong(aggregate, 3);
        BigDecimal netPnl = numberAsBigDecimal(aggregate, 4);
        BigDecimal grossProfit = numberAsBigDecimal(aggregate, 5);
        BigDecimal grossLoss = numberAsBigDecimal(aggregate, 6);
        Map<String, Object> result = buildPerformanceSummary(type, label, count, wins, losses, breakeven, netPnl, grossProfit, grossLoss);
        List<CoinPerformance> coinPerformance = bySymbolRows == null ? List.of() : bySymbolRows.stream()
                .map(row -> new CoinPerformance(
                        row[0] == null ? "" : row[0].toString(),
                        numberAsBigDecimal(row, 1),
                        numberAsLong(row, 2)))
                .toList();
        attachCoinLeaders(result, coinPerformance);
        return result;
    }

    private Map<String, Object> buildPerformanceSummary(
            String type,
            String label,
            long count,
            long wins,
            long losses,
            long breakeven,
            BigDecimal netPnl,
            BigDecimal grossProfit,
            BigDecimal grossLoss) {
        long decided = wins + losses;
        BigDecimal winRate = decided == 0 ? ZERO
                : BigDecimal.valueOf(wins).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(decided), 2, RoundingMode.HALF_UP);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowType", type);
        result.put("label", label);
        result.put("closedTrades", count);
        result.put("wins", wins);
        result.put("losses", losses);
        result.put("breakeven", breakeven);
        result.put("winRatePercent", winRate);
        result.put("netPnlUsdt", netPnl);
        result.put("grossProfitUsdt", grossProfit);
        result.put("grossLossUsdt", grossLoss);
        return result;
    }

    private void attachCoinLeaders(Map<String, Object> result, List<CoinPerformance> coinPerformance) {
        CoinPerformance topWinner = coinPerformance.stream()
                .filter(item -> item.netPnl().signum() > 0)
                .max(Comparator.comparing(CoinPerformance::netPnl))
                .orElse(null);
        CoinPerformance topLoser = coinPerformance.stream()
                .filter(item -> item.netPnl().signum() < 0)
                .min(Comparator.comparing(CoinPerformance::netPnl))
                .orElse(null);
        result.put("topWinner", coinPerformanceDto(topWinner));
        result.put("topLoser", coinPerformanceDto(topLoser));
    }

    private Map<String, Object> coinPerformanceDto(CoinPerformance performance) {
        if (performance == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("symbol", performance.symbol());
        result.put("netPnlUsdt", performance.netPnl());
        result.put("closedTrades", performance.tradeCount());
        return result;
    }

    private record CoinPerformance(String symbol, BigDecimal netPnl, long tradeCount) {}

    private static final class CoinPerformanceAccumulator {
        private BigDecimal netPnl = BigDecimal.ZERO;
        private long tradeCount;

        void add(BigDecimal pnl) {
            netPnl = netPnl.add(pnl);
            tradeCount++;
        }

        BigDecimal netPnl() { return netPnl; }
        long tradeCount() { return tradeCount; }
    }

    private long numberAsLong(Object[] values, int index) {
        if (values == null || index >= values.length || values[index] == null) return 0L;
        return ((Number) values[index]).longValue();
    }

    private BigDecimal numberAsBigDecimal(Object[] values, int index) {
        if (values == null || index >= values.length || values[index] == null) return ZERO;
        Object value = values[index];
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private String normalizePerformanceWindowType(String value) {
        String normalized = value == null || value.isBlank() ? "LAST_TRADES" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("LAST_TRADES", "TODAY", "LAST_DAYS", "DATE_RANGE").contains(normalized))
            throw new IllegalArgumentException("Invalid trade performance window type");
        return normalized;
    }

    @Transactional(readOnly = true)
    public BigDecimal currentPortfolioValue() {
        BigDecimal portfolio = ZERO;
        for (WalletAsset asset : assetRepository.findAll()) {
            portfolio = portfolio.add(asset.getQuantity().multiply(currentPrice(asset.getSymbol())));
        }
        return portfolio;
    }

    private Map<String, Object> dailyTradingSummary(
            WalletSettings settings,
            BigDecimal availableUsdt,
            BigDecimal portfolioValue) {

        int configuredMaximum = settings == null ? 0 : settings.getMaximumDailyNewPositions();
        BigDecimal configuredTradeAmount = settings == null
                ? BigDecimal.valueOf(100)
                : nvl(settings.getBaseTradeAmountUsdt());
        BigDecimal reserve = settings == null ? ZERO : nvl(settings.getMinimumUsdtReserve());
        BigDecimal tradable = availableUsdt.subtract(reserve).max(ZERO);
        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        return dailyStatisticsRepository.findByTradeDate(today)
                .<Map<String, Object>>map(statistics -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("tradeDate", statistics.getTradeDate());
                    result.put("dailyTradeBudgetUsdt", statistics.getDailyTradeBudgetUsdt());
                    result.put("executedBuys", statistics.getExecutedBuys());
                    result.put("maximumNewPositions", statistics.getMaximumNewPositions());
                    result.put("remainingBuys", statistics.getMaximumNewPositions() == 0
                            ? null
                            : Math.max(0, statistics.getMaximumNewPositions() - statistics.getExecutedBuys()));
                    result.put("unlimited", statistics.getMaximumNewPositions() == 0);
                    result.put("startingUsdt", statistics.getStartingUsdt());
                    result.put("currentUsdt", availableUsdt);
                    result.put("startingPortfolioUsdt", statistics.getStartingPortfolioUsdt());
                    result.put("currentPortfolioUsdt", portfolioValue);
                    result.put("budgetLocked", true);
                    return result;
                })
                .orElseGet(() -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    BigDecimal previewBudget = tradable.signum() <= 0
                            ? ZERO
                            : configuredMaximum == 0
                            ? configuredTradeAmount.min(tradable)
                            : tradable.divide(BigDecimal.valueOf(configuredMaximum), SCALE, RoundingMode.DOWN);
                    result.put("tradeDate", today);
                    result.put("dailyTradeBudgetUsdt", previewBudget);
                    result.put("executedBuys", 0);
                    result.put("maximumNewPositions", configuredMaximum);
                    result.put("remainingBuys", configuredMaximum == 0 ? null : configuredMaximum);
                    result.put("unlimited", configuredMaximum == 0);
                    result.put("startingUsdt", availableUsdt);
                    result.put("currentUsdt", availableUsdt);
                    result.put("startingPortfolioUsdt", portfolioValue);
                    result.put("currentPortfolioUsdt", portfolioValue);
                    result.put("budgetLocked", false);
                    return result;
                });
    }

    @Transactional
    public void execute(WalletTradeRequest request) {
        String side = requireOne(request.side(), Set.of("BUY","SELL"), "side");
        String pair = request.symbol().trim().toUpperCase(Locale.ROOT);
        if (!pair.endsWith("USDT") || pair.length() <= 4) throw new IllegalArgumentException("Symbol must be a USDT pair, for example BNBUSDT");
        String assetSymbol = pair.substring(0, pair.length()-4);
        BigDecimal qty = positive(request.quantity(), "Quantity");
        BigDecimal price = positive(request.priceUsdt(), "Price");
        BigDecimal fee = nvl(request.feeUsdt());
        if (fee.signum() < 0) throw new IllegalArgumentException("Fee cannot be negative");
        BigDecimal gross = qty.multiply(price);
        WalletAsset coin = getOrCreate(assetSymbol); WalletAsset usdt = getOrCreate("USDT");
        TradeSignal signal = request.signalId() == null ? null : signalRepository.findById(request.signalId()).orElseThrow(() -> new IllegalArgumentException("Signal not found"));
        BigDecimal costBasis = null, realized = null, realizedPct = null, net;
        if ("BUY".equals(side)) {
            net = gross.add(fee);
            if (usdt.getQuantity().compareTo(net) < 0) throw new IllegalArgumentException("Insufficient USDT balance");
            BigDecimal oldCost = coin.getQuantity().multiply(nvl(coin.getAverageBuyPriceUsdt()));
            BigDecimal newQty = coin.getQuantity().add(qty);
            coin.setAverageBuyPriceUsdt(oldCost.add(gross).divide(newQty, SCALE, RoundingMode.HALF_UP));
            coin.setQuantity(newQty); usdt.setQuantity(usdt.getQuantity().subtract(net));
        } else {
            if (coin.getQuantity().compareTo(qty) < 0) throw new IllegalArgumentException("Insufficient " + assetSymbol + " balance");
            net = gross.subtract(fee); costBasis = qty.multiply(nvl(coin.getAverageBuyPriceUsdt()));
            realized = net.subtract(costBasis); realizedPct = percent(realized, costBasis);
            coin.setQuantity(coin.getQuantity().subtract(qty));
            if (coin.getQuantity().signum() == 0) coin.setAverageBuyPriceUsdt(null);
            usdt.setQuantity(usdt.getQuantity().add(net));
        }
        assetRepository.save(coin); assetRepository.save(usdt);
        tradeRepository.save(WalletTrade.builder().signal(signal).symbol(pair).side(side).quantity(qty).priceUsdt(price)
                .grossAmountUsdt(gross).feeUsdt(fee).netAmountUsdt(net).costBasisUsdt(costBasis)
                .realizedPnlUsdt(realized).realizedPnlPercent(realizedPct)
                .executionType(Optional.ofNullable(request.executionType()).orElse("MANUAL").toUpperCase(Locale.ROOT))
                .executionReason("MANUAL_" + side)
                .status("EXECUTED").executedAt(Instant.now()).notes(request.notes())
                .executionMessage("Manual " + side + " applied to wallet for " + pair)
                .build());
        captureSnapshot();
    }

    @Transactional
    public void captureSnapshot() {
        Map<String,Object> o = overviewWithoutHistory();
        snapshotRepository.save(WalletSnapshot.builder().portfolioValueUsdt((BigDecimal)o.get("portfolioValueUsdt"))
                .netInvestedUsdt((BigDecimal)o.get("netInvestedUsdt")).totalPnlUsdt((BigDecimal)o.get("totalPnlUsdt"))
                .totalReturnPercent((BigDecimal)o.get("totalReturnPercent")).realizedPnlUsdt((BigDecimal)o.get("realizedPnlUsdt"))
                .unrealizedPnlUsdt((BigDecimal)o.get("unrealizedPnlUsdt")).availableUsdt((BigDecimal)o.get("availableUsdt"))
                .capturedAt(Instant.now()).build());
    }
    private Map<String,Object> overviewWithoutHistory() {
        BigDecimal portfolio=ZERO, unrealized=ZERO;
        for (WalletAsset a: assetRepository.findAll()) { BigDecimal p=currentPrice(a.getSymbol()); BigDecimal v=a.getQuantity().multiply(p); portfolio=portfolio.add(v); if(!"USDT".equals(a.getSymbol())) unrealized=unrealized.add(v.subtract(a.getQuantity().multiply(nvl(a.getAverageBuyPriceUsdt())))); }
        BigDecimal invested=netInvested(), realized=nvl(tradeRepository.totalRealizedPnl()), total=portfolio.subtract(invested), available=assetRepository.findBySymbol("USDT").map(WalletAsset::getQuantity).orElse(ZERO);
        return Map.of("portfolioValueUsdt",portfolio,"netInvestedUsdt",invested,"totalPnlUsdt",total,"totalReturnPercent",percent(total,invested),"realizedPnlUsdt",realized,"unrealizedPnlUsdt",unrealized,"availableUsdt",available);
    }
    private BigDecimal netInvested() { return nvl(cashFlowRepository.netInvestedUsdt()); }
    private BigDecimal currentPrice(String asset) { if ("USDT".equals(asset)) return BigDecimal.ONE; return candleRepository.findFirstBySymbolAndIntervalCodeAndClosedTrueOrderByCloseTimeDesc(asset+"USDT","1m").map(Candle::getClosePrice).orElse(ZERO); }
    private WalletAsset getOrCreate(String symbol) { return assetRepository.findBySymbol(symbol).orElseGet(() -> assetRepository.save(WalletAsset.builder().symbol(symbol).quantity(ZERO).averageBuyPriceUsdt("USDT".equals(symbol)?BigDecimal.ONE:null).enabled(true).build())); }
    private Map<String,Object> tradeDto(WalletTrade t) { Map<String,Object> m=new LinkedHashMap<>(); m.put("id",t.getId()); m.put("signalId",t.getSignal()==null?null:t.getSignal().getId()); m.put("positionAnalysisId",t.getPositionAnalysis()==null?null:t.getPositionAnalysis().getId()); m.put("symbol",t.getSymbol()); m.put("side",t.getSide()); m.put("quantity",t.getQuantity()); m.put("priceUsdt",t.getPriceUsdt()); m.put("grossAmountUsdt",t.getGrossAmountUsdt()); m.put("feeUsdt",t.getFeeUsdt()); m.put("netAmountUsdt",t.getNetAmountUsdt()); m.put("realizedPnlUsdt",t.getRealizedPnlUsdt()); m.put("realizedPnlPercent",t.getRealizedPnlPercent()); m.put("executionReason",t.getExecutionReason()); m.put("executionMessage",t.getExecutionMessage()); m.put("executedAt",t.getExecutedAt()); return m; }
    private BigDecimal percent(BigDecimal value, BigDecimal base) { return base==null||base.signum()==0?ZERO:value.divide(base,8,RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)); }
    private BigDecimal nvl(BigDecimal v){return v==null?ZERO:v;} private BigDecimal positive(BigDecimal v,String n){if(v==null||v.signum()<=0)throw new IllegalArgumentException(n+" must be greater than zero");return v;} private BigDecimal nonNegative(BigDecimal v,String n){if(v==null||v.signum()<0)throw new IllegalArgumentException(n+" cannot be negative");return v;} private BigDecimal nonNegativeNullable(BigDecimal v,String n){if(v==null)return null;return nonNegative(v,n);} private String normalizeAsset(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("Symbol is required");String s=v.trim().toUpperCase(Locale.ROOT);return s.endsWith("USDT")&&s.length()>4?s.substring(0,s.length()-4):s;} private String requireOne(String v,Set<String>a,String n){if(v==null||!a.contains(v.trim().toUpperCase(Locale.ROOT)))throw new IllegalArgumentException("Invalid "+n);return v.trim().toUpperCase(Locale.ROOT);}
}
