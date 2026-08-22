package com.crypto.wallet.service;

import com.crypto.domain.TradeSignal;
import com.crypto.position.domain.PositionAnalysis;
import com.crypto.position.domain.PositionRecommendation;
import com.crypto.wallet.domain.*;
import com.crypto.wallet.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class WalletAutoExecutionService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int SCALE = 12;

    private final WalletAssetRepository assetRepository;
    private final WalletTradeRepository tradeRepository;
    private final WalletManagedPositionRepository managedPositionRepository;
    private final WalletSettingsRepository settingsRepository;
    private final WalletExecutionSizingPolicy executionSizingPolicy;
    private final WalletDailyStatisticsRepository dailyStatisticsRepository;
    private final WalletService walletService;
    private final ObjectMapper objectMapper;

    @Transactional
    public synchronized boolean executeBuy(TradeSignal signal) {
        return executeBuy(signal, 100, "Full-size execution");
    }

    @Transactional
    public synchronized boolean executeBuy(TradeSignal signal, int positionPercent, String executionExplanation) {
        return executeBuy(signal, positionPercent, executionExplanation, "ENTRY_BUY", 0);
    }

    @Transactional
    public synchronized boolean executeBuy(TradeSignal signal, int positionPercent, String executionExplanation,
                                           String entryStage, int entryQualityScore) {
        if (signal == null || signal.getId() == null) return false;
        WalletSettings settings = settings();
        int requestedPositionPercent = Math.max(1, Math.min(100, positionPercent));

        String pair = normalizePair(signal.getSymbol());
        WalletManagedPosition existingPosition = managedPositionRepository
                .findTopBySymbolAndStatusOrderByOpenedAtDesc(pair, "OPEN")
                .orElse(null);
        int currentAllocatedPercent = existingPosition == null ? 0 : Math.max(0, existingPosition.getAllocatedPositionPercent());

        String key = signal.getId() + ":BUY";
        if (tradeRepository.existsByExecutionKey(key)) return true;

        String assetSymbol = pair.substring(0, pair.length() - 4);
        BigDecimal price = positive(signal.getLatestPrice());
        WalletAsset usdt = getOrCreate("USDT");
        WalletDailyStatistics daily = dailyStatistics(settings, usdt);

        boolean newPosition = existingPosition == null;
        WalletExecutionSizingPolicy.Plan sizing = executionSizingPolicy.plan(
                usdt.getQuantity(), settings.getMinimumUsdtReserve(), daily.getDailyTradeBudgetUsdt(),
                requestedPositionPercent, currentAllocatedPercent, newPosition,
                daily.getMaximumNewPositions(), daily.getExecutedBuys(), price);
        if (!sizing.allowed()) return false;
        int normalizedPositionPercent = sizing.normalizedPositionPercent();
        BigDecimal spend = sizing.spend();
        BigDecimal quantity = sizing.quantity();

        // FIX-037: debit USDT atomically before mutating the purchased asset. A stale
        // Java-side WalletAsset instance must never overwrite a concurrent SELL credit.
        if (assetRepository.debitQuantityIfSufficient("USDT", spend) != 1) return false;
        WalletAsset coin = getOrCreate(assetSymbol);
        BigDecimal oldCost = coin.getQuantity().multiply(nvl(coin.getAverageBuyPriceUsdt()));
        BigDecimal newQuantity = coin.getQuantity().add(quantity);
        coin.setQuantity(newQuantity);
        coin.setAverageBuyPriceUsdt(oldCost.add(spend)
                .divide(newQuantity, SCALE, RoundingMode.HALF_UP));
        assetRepository.save(coin);
        // clearAutomatically on the atomic update invalidates the stale persistence context;
        // reload USDT so statistics/snapshots use the committed post-debit balance.
        usdt = getOrCreate("USDT");

        WalletManagedPosition position = existingPosition != null
                ? existingPosition
                : newManagedPosition(pair, signal);
        position.setQuantity(position.getQuantity().add(quantity));
        position.setTotalCostUsdt(position.getTotalCostUsdt().add(spend));
        position.setAverageEntryPriceUsdt(position.getTotalCostUsdt()
                .divide(position.getQuantity(), SCALE, RoundingMode.HALF_UP));
        position.setAllocatedPositionPercent(Math.min(100,
                position.getAllocatedPositionPercent() + normalizedPositionPercent));
        position.setEntryStage(entryStage == null || entryStage.isBlank() ? "ENTRY_BUY" : entryStage);
        position.setEntryQualityScore(Math.max(0, entryQualityScore));
        if (!newPosition) {
            position.setLastScaleInAt(Instant.now());
            if (signal.getStopLoss() != null && signal.getStopLoss().signum() > 0
                    && (position.getStopLossUsdt() == null || signal.getStopLoss().compareTo(position.getStopLossUsdt()) > 0)) {
                position.setStopLossUsdt(signal.getStopLoss());
            }
            if (signal.getTakeProfit() != null && signal.getTakeProfit().signum() > 0
                    && (position.getTakeProfitUsdt() == null || signal.getTakeProfit().compareTo(position.getTakeProfitUsdt()) > 0)) {
                position.setTakeProfitUsdt(signal.getTakeProfit());
            }
        }
        position.setUpdatedAt(Instant.now());
        managedPositionRepository.save(position);

        tradeRepository.save(WalletTrade.builder()
                .signal(signal)
                .executionKey(key)
                .symbol(pair)
                .side("BUY")
                .quantity(quantity)
                .priceUsdt(price)
                .grossAmountUsdt(spend)
                .feeUsdt(ZERO)
                .netAmountUsdt(spend)
                .executionType("PAPER_AUTO")
                .executionReason(entryStage == null || entryStage.isBlank() ? "ENTRY_BUY" : entryStage)
                .status("EXECUTED")
                .executedAt(Instant.now())
                .notes("Automatic wallet BUY using " + normalizedPositionPercent + "% of the configured BUY budget")
                .executionMessage("BUY decision from trade signal #" + signal.getId()
                        + " applied to wallet for " + pair + " at " + normalizedPositionPercent
                        + "% size. " + (executionExplanation == null ? "" : executionExplanation))
                .build());

        if (newPosition) {
            daily.setExecutedBuys(daily.getExecutedBuys() + 1);
        }
        daily.setEndingUsdt(usdt.getQuantity());
        daily.setEndingPortfolioUsdt(walletService.currentPortfolioValue());
        daily.setUpdatedAt(Instant.now());
        dailyStatisticsRepository.save(daily);
        walletService.captureSnapshot();
        return true;
    }

    @Transactional
    public void executeSell(TradeSignal signal) {
        executeSignalLinkedExit(signal, "SIGNAL_SELL",
                signal == null || signal.getId() == null ? "Signal SELL"
                        : "SELL decision from trade signal #" + signal.getId() + " applied to wallet for " + normalizePair(signal.getSymbol()));
    }

    /**
     * FIX-028: execute the same signal-linked wallet liquidation while preserving the
     * position engine's actual terminal reason. This method intentionally keeps the same
     * idempotency key (signalId:SELL), quantities, balances and close mechanics as the
     * legacy executeSell path; only audit metadata is corrected.
     */
    @Transactional
    public synchronized void executeSignalLinkedExit(TradeSignal signal, String executionReason, String executionMessage) {
        if (signal == null || signal.getId() == null) return;
        String key = signal.getId() + ":SELL";
        if (tradeRepository.existsByExecutionKey(key)) return;

        String pair = normalizePair(signal.getSymbol());
        WalletManagedPosition position = managedPositionRepository.findTopBySymbolAndStatusOrderByOpenedAtDesc(pair, "OPEN").orElse(null);
        if (position == null || position.getQuantity().signum() <= 0) return;

        String assetSymbol = pair.substring(0, pair.length() - 4);
        WalletAsset coin = getOrCreate(assetSymbol);
        BigDecimal quantity = position.getQuantity().min(coin.getQuantity());
        if (quantity.signum() <= 0) return;

        BigDecimal price = positive(signal.getLatestPrice());
        BigDecimal gross = quantity.multiply(price);
        BigDecimal costBasis = position.getAverageEntryPriceUsdt().multiply(quantity);
        BigDecimal realized = gross.subtract(costBasis);
        BigDecimal realizedPercent = costBasis.signum() == 0 ? ZERO : realized
                .divide(costBasis, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        coin.setQuantity(coin.getQuantity().subtract(quantity));
        if (coin.getQuantity().signum() == 0) coin.setAverageBuyPriceUsdt(null);
        assetRepository.save(coin);
        // FIX-037: credit SELL proceeds atomically so a concurrent BUY cannot overwrite them.
        if (assetRepository.creditQuantity("USDT", gross) != 1) {
            throw new IllegalStateException("Unable to credit USDT wallet balance");
        }
        // FIX-037: keep a final post-credit balance snapshot for lambda-based daily-stat updates.
        // Using a separate final variable also avoids capturing a reassigned local variable.
        final BigDecimal endingUsdt = getOrCreate("USDT").getQuantity();

        position.setQuantity(ZERO);
        position.setTotalCostUsdt(ZERO);
        position.setAverageEntryPriceUsdt(ZERO);
        position.setStatus("CLOSED");
        position.setUpdatedAt(Instant.now());
        managedPositionRepository.save(position);

        String reason = executionReason == null || executionReason.isBlank()
                ? "SIGNAL_SELL" : executionReason.trim().toUpperCase(Locale.ROOT);
        tradeRepository.save(WalletTrade.builder()
                .signal(signal).executionKey(key).symbol(pair).side("SELL")
                .quantity(quantity).priceUsdt(price).grossAmountUsdt(gross)
                .feeUsdt(ZERO).netAmountUsdt(gross).costBasisUsdt(costBasis)
                .realizedPnlUsdt(realized).realizedPnlPercent(realizedPercent)
                .executionType("PAPER_AUTO").executionReason(reason)
                .status("EXECUTED").executedAt(Instant.now())
                .notes("Automatic paper exit from persisted position trigger")
                .executionMessage(executionMessage == null || executionMessage.isBlank()
                        ? reason + " executed using trade signal #" + signal.getId() + " as market context"
                        : executionMessage)
                .build());

        dailyStatisticsRepository.findForUpdateByTradeDate(LocalDate.now(ZoneOffset.UTC))
                .ifPresent(daily -> {
                    daily.setEndingUsdt(endingUsdt);
                    daily.setEndingPortfolioUsdt(walletService.currentPortfolioValue());
                    daily.setUpdatedAt(Instant.now());
                    dailyStatisticsRepository.save(daily);
                });
        walletService.captureSnapshot();
    }

    /**
     * Applies an already-persisted Position Manager STOP_LOSS decision to the wallet.
     * Other position recommendations remain advisory-only until separately validated.
     */
    @Transactional
    public synchronized boolean executePositionStopLoss(PositionAnalysis analysis) {
        if (analysis == null || analysis.getId() == null
                || analysis.getRecommendation() != PositionRecommendation.STOP_LOSS) {
            return false;
        }

        String key = "POSITION_ANALYSIS:" + analysis.getId() + ":STOP_LOSS";
        if (tradeRepository.existsByExecutionKey(key)) return true;

        String pair = normalizePair(analysis.getSymbol());
        WalletManagedPosition position = managedPositionRepository
                .findTopBySymbolAndStatusOrderByOpenedAtDesc(pair, "OPEN")
                .orElse(null);
        if (position == null || position.getQuantity() == null
                || position.getQuantity().signum() <= 0) {
            return false;
        }

        String assetSymbol = pair.substring(0, pair.length() - 4);
        WalletAsset coin = getOrCreate(assetSymbol);
        BigDecimal quantity = position.getQuantity().min(coin.getQuantity());
        if (quantity.signum() <= 0) return false;

        BigDecimal price = positive(analysis.getCurrentPriceUsdt());
        BigDecimal gross = quantity.multiply(price);
        BigDecimal costBasis = position.getAverageEntryPriceUsdt().multiply(quantity);
        BigDecimal realized = gross.subtract(costBasis);
        BigDecimal realizedPercent = costBasis.signum() == 0 ? ZERO : realized
                .divide(costBasis, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        coin.setQuantity(coin.getQuantity().subtract(quantity));
        if (coin.getQuantity().signum() == 0) coin.setAverageBuyPriceUsdt(null);
        assetRepository.save(coin);
        // FIX-037: credit SELL proceeds atomically so a concurrent BUY cannot overwrite them.
        if (assetRepository.creditQuantity("USDT", gross) != 1) {
            throw new IllegalStateException("Unable to credit USDT wallet balance");
        }
        // FIX-037: keep a final post-credit balance snapshot for lambda-based daily-stat updates.
        // Using a separate final variable also avoids capturing a reassigned local variable.
        final BigDecimal endingUsdt = getOrCreate("USDT").getQuantity();

        position.setQuantity(ZERO);
        position.setTotalCostUsdt(ZERO);
        position.setAverageEntryPriceUsdt(ZERO);
        position.setStatus("CLOSED");
        position.setUpdatedAt(Instant.now());
        managedPositionRepository.save(position);

        TradeSignal sourceSignal = analysis.getTradeSignal();
        tradeRepository.save(WalletTrade.builder()
                .signal(sourceSignal)
                .positionAnalysis(analysis)
                .executionKey(key)
                .symbol(pair)
                .side("SELL")
                .quantity(quantity)
                .priceUsdt(price)
                .grossAmountUsdt(gross)
                .feeUsdt(ZERO)
                .netAmountUsdt(gross)
                .costBasisUsdt(costBasis)
                .realizedPnlUsdt(realized)
                .realizedPnlPercent(realizedPercent)
                .executionType("PAPER_AUTO")
                .executionReason("POSITION_STOP_LOSS")
                .status("EXECUTED")
                .executedAt(Instant.now())
                .notes("Position Manager STOP_LOSS applied to wallet")
                .executionMessage("Position analysis #" + analysis.getId()
                        + " generated STOP_LOSS and sold " + quantity + " " + assetSymbol
                        + " at " + price + " USDT")
                .build());

        dailyStatisticsRepository.findForUpdateByTradeDate(LocalDate.now(ZoneOffset.UTC))
                .ifPresent(daily -> {
                    daily.setEndingUsdt(endingUsdt);
                    daily.setEndingPortfolioUsdt(walletService.currentPortfolioValue());
                    daily.setUpdatedAt(Instant.now());
                    dailyStatisticsRepository.save(daily);
                });
        walletService.captureSnapshot();
        return true;
    }


    /**
     * Applies a Dynamic Profit Lock exit generated by the Position Manager.
     * The source signal is used only for traceability; it does not need to be a SELL signal.
     */
    @Transactional
    public synchronized boolean executeProfitLock(TradeSignal sourceSignal, BigDecimal executionPrice, BigDecimal protectedPrice) {
        if (sourceSignal == null || sourceSignal.getId() == null || executionPrice == null || executionPrice.signum() <= 0) {
            return false;
        }

        String pair = normalizePair(sourceSignal.getSymbol());
        WalletManagedPosition position = managedPositionRepository
                .findTopBySymbolAndStatusOrderByOpenedAtDesc(pair, "OPEN")
                .orElse(null);
        if (position == null || position.getId() == null || position.getQuantity() == null
                || position.getQuantity().signum() <= 0) {
            return false;
        }

        String key = "PROFIT_LOCK:" + position.getId();
        if (tradeRepository.existsByExecutionKey(key)) return true;

        String assetSymbol = pair.substring(0, pair.length() - 4);
        WalletAsset coin = getOrCreate(assetSymbol);
        BigDecimal quantity = position.getQuantity().min(coin.getQuantity());
        if (quantity.signum() <= 0) return false;

        BigDecimal price = positive(executionPrice);
        BigDecimal gross = quantity.multiply(price);
        BigDecimal costBasis = position.getAverageEntryPriceUsdt().multiply(quantity);
        BigDecimal realized = gross.subtract(costBasis);
        BigDecimal realizedPercent = costBasis.signum() == 0 ? ZERO : realized
                .divide(costBasis, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        coin.setQuantity(coin.getQuantity().subtract(quantity));
        if (coin.getQuantity().signum() == 0) coin.setAverageBuyPriceUsdt(null);
        assetRepository.save(coin);
        // FIX-037: credit SELL proceeds atomically so a concurrent BUY cannot overwrite them.
        if (assetRepository.creditQuantity("USDT", gross) != 1) {
            throw new IllegalStateException("Unable to credit USDT wallet balance");
        }
        // FIX-037: keep a final post-credit balance snapshot for lambda-based daily-stat updates.
        // Using a separate final variable also avoids capturing a reassigned local variable.
        final BigDecimal endingUsdt = getOrCreate("USDT").getQuantity();

        position.setQuantity(ZERO);
        position.setTotalCostUsdt(ZERO);
        position.setAverageEntryPriceUsdt(ZERO);
        position.setStatus("CLOSED");
        position.setUpdatedAt(Instant.now());
        managedPositionRepository.save(position);

        tradeRepository.save(WalletTrade.builder()
                .signal(sourceSignal)
                .executionKey(key)
                .symbol(pair)
                .side("SELL")
                .quantity(quantity)
                .priceUsdt(price)
                .grossAmountUsdt(gross)
                .feeUsdt(ZERO)
                .netAmountUsdt(gross)
                .costBasisUsdt(costBasis)
                .realizedPnlUsdt(realized)
                .realizedPnlPercent(realizedPercent)
                .executionType("PAPER_AUTO")
                .executionReason("POSITION_PROFIT_LOCK")
                .status("EXECUTED")
                .executedAt(Instant.now())
                .notes("Dynamic Profit Lock applied to wallet")
                .executionMessage("Dynamic Profit Lock for trade signal #" + sourceSignal.getId()
                        + " sold " + quantity + " " + assetSymbol + " at " + price
                        + " USDT; protected level was " + protectedPrice + " USDT")
                .build());

        dailyStatisticsRepository.findForUpdateByTradeDate(LocalDate.now(ZoneOffset.UTC))
                .ifPresent(daily -> {
                    daily.setEndingUsdt(endingUsdt);
                    daily.setEndingPortfolioUsdt(walletService.currentPortfolioValue());
                    daily.setUpdatedAt(Instant.now());
                    dailyStatisticsRepository.save(daily);
                });
        walletService.captureSnapshot();
        return true;
    }


    /**
     * Executes a mechanical live-price exit (TP / SL / Profit Lock) without requiring
     * a newly generated trade signal. The open wallet position is the source of truth.
     */
    @Transactional
    public synchronized boolean executeMechanicalExit(
            String symbol,
            BigDecimal executionPrice,
            String executionReason,
            String executionMessage
    ) {
        String pair = normalizePair(symbol);
        WalletManagedPosition position = managedPositionRepository
                .findFirstBySymbolAndStatusOrderByOpenedAtDesc(pair, "OPEN")
                .orElse(null);
        if (position == null || position.getId() == null || position.getQuantity() == null
                || position.getQuantity().signum() <= 0) {
            return false;
        }

        String reason = executionReason == null || executionReason.isBlank()
                ? "MECHANICAL_EXIT"
                : executionReason.trim().toUpperCase(Locale.ROOT);
        String key = "POSITION:" + position.getId() + ":" + reason;
        if (tradeRepository.existsByExecutionKey(key)) return true;

        String assetSymbol = pair.substring(0, pair.length() - 4);
        WalletAsset coin = getOrCreate(assetSymbol);
        BigDecimal quantity = position.getQuantity().min(coin.getQuantity());
        if (quantity.signum() <= 0) return false;

        BigDecimal price = positive(executionPrice);
        BigDecimal gross = quantity.multiply(price);
        BigDecimal costBasis = position.getAverageEntryPriceUsdt().multiply(quantity);
        BigDecimal realized = gross.subtract(costBasis);
        BigDecimal realizedPercent = costBasis.signum() == 0 ? ZERO : realized
                .divide(costBasis, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        coin.setQuantity(coin.getQuantity().subtract(quantity));
        if (coin.getQuantity().signum() == 0) coin.setAverageBuyPriceUsdt(null);
        assetRepository.save(coin);
        // FIX-037: credit SELL proceeds atomically so a concurrent BUY cannot overwrite them.
        if (assetRepository.creditQuantity("USDT", gross) != 1) {
            throw new IllegalStateException("Unable to credit USDT wallet balance");
        }
        // FIX-037: keep a final post-credit balance snapshot for lambda-based daily-stat updates.
        // Using a separate final variable also avoids capturing a reassigned local variable.
        final BigDecimal endingUsdt = getOrCreate("USDT").getQuantity();

        position.setQuantity(ZERO);
        position.setTotalCostUsdt(ZERO);
        position.setAverageEntryPriceUsdt(ZERO);
        position.setStatus("CLOSED");
        position.setUpdatedAt(Instant.now());
        managedPositionRepository.save(position);

        tradeRepository.save(WalletTrade.builder()
                .executionKey(key)
                .symbol(pair)
                .side("SELL")
                .quantity(quantity)
                .priceUsdt(price)
                .grossAmountUsdt(gross)
                .feeUsdt(ZERO)
                .netAmountUsdt(gross)
                .costBasisUsdt(costBasis)
                .realizedPnlUsdt(realized)
                .realizedPnlPercent(realizedPercent)
                .executionType("PAPER_AUTO")
                .executionReason(reason)
                .status("EXECUTED")
                .executedAt(Instant.now())
                .notes("Mechanical live-price position exit")
                .executionMessage(executionMessage == null ? reason + " triggered at " + price : executionMessage)
                .build());

        dailyStatisticsRepository.findForUpdateByTradeDate(LocalDate.now(ZoneOffset.UTC))
                .ifPresent(daily -> {
                    daily.setEndingUsdt(endingUsdt);
                    daily.setEndingPortfolioUsdt(walletService.currentPortfolioValue());
                    daily.setUpdatedAt(Instant.now());
                    dailyStatisticsRepository.save(daily);
                });
        walletService.captureSnapshot();
        return true;
    }

    private WalletDailyStatistics dailyStatistics(WalletSettings settings, WalletAsset usdt) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        WalletDailyStatistics existing = dailyStatisticsRepository
                .findForUpdateByTradeDate(today)
                .orElse(null);
        if (existing != null) return existing;

        int maximum = settings.getMaximumDailyNewPositions();
        BigDecimal budget = executionSizingPolicy.initialDailyBudget(
                usdt.getQuantity(), settings.getMinimumUsdtReserve(),
                settings.getBaseTradeAmountUsdt(), maximum);
        BigDecimal portfolio = walletService.currentPortfolioValue();
        Instant now = Instant.now();

        return dailyStatisticsRepository.save(WalletDailyStatistics.builder()
                .tradeDate(today)
                .maximumNewPositions(maximum)
                .dailyTradeBudgetUsdt(budget)
                .executedBuys(0)
                .startingUsdt(usdt.getQuantity())
                .endingUsdt(usdt.getQuantity())
                .startingPortfolioUsdt(portfolio)
                .endingPortfolioUsdt(portfolio)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private WalletSettings settings() {
        return settingsRepository.findById(1L).orElseGet(() -> settingsRepository.save(
                WalletSettings.builder().id(1L).baseTradeAmountUsdt(BigDecimal.valueOf(100))
                        .minimumUsdtReserve(ZERO).maximumDailyNewPositions(0)
                        .performanceWindowType("LAST_TRADES").performanceTradeCount(20).performancePeriodDays(1)
                        .dashboardIntervals("1m,5m,1h,4h,1d")
                        .requireNewBuyTransition(true)
                        .executionProfile("BALANCED")
                        .dynamicProfitLockEnabled(true)
                        .profitLockActivationPercent(BigDecimal.valueOf(70))
                        .profitLockInitialPercent(BigDecimal.valueOf(40))
                        .profitLockTrailStepPercent(BigDecimal.valueOf(10))
                        .updatedAt(Instant.now()).build()));
    }

    private WalletAsset getOrCreate(String symbol) {
        return assetRepository.findBySymbol(symbol).orElseGet(() -> assetRepository.save(
                WalletAsset.builder().symbol(symbol).quantity(ZERO)
                        .averageBuyPriceUsdt("USDT".equals(symbol) ? BigDecimal.ONE : null)
                        .enabled(true).build()));
    }
    private String normalizePair(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Symbol is required");
        String pair = value.trim().toUpperCase(Locale.ROOT);
        if (!pair.endsWith("USDT")) throw new IllegalArgumentException("Only USDT pairs are supported");
        return pair;
    }
    private BigDecimal positive(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException("Price must be positive");
        return value;
    }

    private WalletManagedPosition newManagedPosition(String pair, TradeSignal signal) {
        Instant now = Instant.now();
        return WalletManagedPosition.builder()
                .symbol(pair)
                .quantity(ZERO)
                .averageEntryPriceUsdt(ZERO)
                .totalCostUsdt(ZERO)
                .status("OPEN")
                .openedAt(now)
                .updatedAt(now)
                .entrySignalId(signal.getId())
                .entryConfidence(signal.getConfidenceScore())
                .entryTotalScore(signal.getTotalScore())
                .entryTrendScore(signal.getTrendScore())
                .entryStructureScore(signal.getTrendStructureScore())
                .entryMomentumScore(signal.getMomentumScore())
                .entryVolumeScore(signal.getVolumeScore())
                .entrySentimentScore(signal.getSentimentScore())
                .entryFundamentalScore(signal.getFundamentalScore())
                .entryDecision(signal.getDecision() == null ? null : signal.getDecision().name())
                .entryDecisionPathJson(signal.getDecisionPath())
                .entryAnalysisSnapshotJson(entrySnapshotJson(signal))
                .stopLossUsdt(signal.getStopLoss())
                .takeProfitUsdt(signal.getTakeProfit())
                .highestPriceUsdt(signal.getLatestPrice())
                .profitLockActive(false)
                .profitLockPriceUsdt(null)
                .profitLockProgressPercent(BigDecimal.ZERO)
                .profitLockActivatedAt(null)
                .entryStage("NONE")
                .allocatedPositionPercent(0)
                .entryQualityScore(0)
                .lastScaleInAt(null)
                .build();
    }

    private String entrySnapshotJson(TradeSignal signal) {
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("signalId", signal.getId());
        snapshot.put("symbol", signal.getSymbol());
        snapshot.put("interval", signal.getInterval());
        snapshot.put("generatedAt", signal.getGeneratedAt());
        snapshot.put("entryPriceUsdt", signal.getLatestPrice());
        snapshot.put("decision", signal.getDecision());
        snapshot.put("originalDecision", signal.getOriginalDecision());
        snapshot.put("confidence", signal.getConfidenceScore());
        snapshot.put("totalScore", signal.getTotalScore());
        snapshot.put("trendScore", signal.getTrendScore());
        snapshot.put("structureScore", signal.getTrendStructureScore());
        snapshot.put("momentumScore", signal.getMomentumScore());
        snapshot.put("volumeScore", signal.getVolumeScore());
        snapshot.put("sentimentScore", signal.getSentimentScore());
        snapshot.put("fundamentalScore", signal.getFundamentalScore());
        snapshot.put("stopLossUsdt", signal.getStopLoss());
        snapshot.put("takeProfitUsdt", signal.getTakeProfit());
        snapshot.put("selectedStrategy", signal.getSelectedStrategy());
        snapshot.put("marketRegime", signal.getMarketRegime());
        snapshot.put("analysisBreakdown", signal.getAnalysisBreakdown());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize immutable BUY thesis", ex);
        }
    }
    private BigDecimal nvl(BigDecimal value) { return value == null ? ZERO : value; }
}
