package com.crypto.wallet.service;

import com.crypto.domain.Candle;
import com.crypto.domain.TradeSignal;
import com.crypto.repository.CandleRepository;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.wallet.domain.*;
import com.crypto.wallet.dto.*;
import com.crypto.wallet.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
        result.put("portfolioStatus", totalPnl.signum() >= 0 ? "WINNING" : "LOSING");
        BigDecimal finalPortfolioValue = portfolio;
        BigDecimal change24h = snapshotRepository.findTop200ByOrderByCapturedAtDesc().stream()
                .filter(x -> x.getCapturedAt().isAfter(Instant.now().minusSeconds(86400)))
                .min(Comparator.comparing(WalletSnapshot::getCapturedAt))
                .map(x -> finalPortfolioValue.subtract(x.getPortfolioValueUsdt()))
                .orElse(ZERO);
        result.put("change24hUsdt", change24h);
        result.put("assets", rows);
        result.put("trades", tradeRepository.findTop100ByOrderByExecutedAtDesc().stream().map(this::tradeDto).toList());
        result.put("cashFlows", cashFlowRepository.findTop100ByOrderByOccurredAtDesc());
        result.put("snapshots", snapshotRepository.findTop200ByOrderByCapturedAtDesc().stream().sorted(Comparator.comparing(WalletSnapshot::getCapturedAt)).toList());
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

        WalletSettings settings = settingsRepository.findById(1L).orElseGet(() -> WalletSettings.builder()
                .id(1L)
                .baseTradeAmountUsdt(BigDecimal.valueOf(100))
                .build());
        settings.setMinimumUsdtReserve(request.minimumUsdtReserve());
        settings.setBaseTradeAmountUsdt(request.baseTradeAmountUsdt());
        settings.setMaximumDailyNewPositions(request.maximumDailyNewPositions());
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
    private BigDecimal netInvested() { return cashFlowRepository.findAll().stream().map(f -> "DEPOSIT".equals(f.getFlowType()) ? f.getAmountUsdt() : f.getAmountUsdt().negate()).reduce(ZERO, BigDecimal::add); }
    private BigDecimal currentPrice(String asset) { if ("USDT".equals(asset)) return BigDecimal.ONE; return candleRepository.findFirstBySymbolAndIntervalCodeAndClosedTrueOrderByCloseTimeDesc(asset+"USDT","1m").map(Candle::getClosePrice).orElse(ZERO); }
    private WalletAsset getOrCreate(String symbol) { return assetRepository.findBySymbol(symbol).orElseGet(() -> assetRepository.save(WalletAsset.builder().symbol(symbol).quantity(ZERO).averageBuyPriceUsdt("USDT".equals(symbol)?BigDecimal.ONE:null).enabled(true).build())); }
    private Map<String,Object> tradeDto(WalletTrade t) { Map<String,Object> m=new LinkedHashMap<>(); m.put("id",t.getId()); m.put("signalId",t.getSignal()==null?null:t.getSignal().getId()); m.put("positionAnalysisId",t.getPositionAnalysis()==null?null:t.getPositionAnalysis().getId()); m.put("symbol",t.getSymbol()); m.put("side",t.getSide()); m.put("quantity",t.getQuantity()); m.put("priceUsdt",t.getPriceUsdt()); m.put("grossAmountUsdt",t.getGrossAmountUsdt()); m.put("feeUsdt",t.getFeeUsdt()); m.put("netAmountUsdt",t.getNetAmountUsdt()); m.put("realizedPnlUsdt",t.getRealizedPnlUsdt()); m.put("realizedPnlPercent",t.getRealizedPnlPercent()); m.put("executionReason",t.getExecutionReason()); m.put("executionMessage",t.getExecutionMessage()); m.put("executedAt",t.getExecutedAt()); return m; }
    private BigDecimal percent(BigDecimal value, BigDecimal base) { return base==null||base.signum()==0?ZERO:value.divide(base,8,RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)); }
    private BigDecimal nvl(BigDecimal v){return v==null?ZERO:v;} private BigDecimal positive(BigDecimal v,String n){if(v==null||v.signum()<=0)throw new IllegalArgumentException(n+" must be greater than zero");return v;} private BigDecimal nonNegative(BigDecimal v,String n){if(v==null||v.signum()<0)throw new IllegalArgumentException(n+" cannot be negative");return v;} private BigDecimal nonNegativeNullable(BigDecimal v,String n){if(v==null)return null;return nonNegative(v,n);} private String normalizeAsset(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("Symbol is required");String s=v.trim().toUpperCase(Locale.ROOT);return s.endsWith("USDT")&&s.length()>4?s.substring(0,s.length()-4):s;} private String requireOne(String v,Set<String>a,String n){if(v==null||!a.contains(v.trim().toUpperCase(Locale.ROOT)))throw new IllegalArgumentException("Invalid "+n);return v.trim().toUpperCase(Locale.ROOT);}
}
