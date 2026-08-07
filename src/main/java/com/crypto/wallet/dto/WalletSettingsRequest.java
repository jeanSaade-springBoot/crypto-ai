package com.crypto.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WalletSettingsRequest(
        BigDecimal minimumUsdtReserve,
        BigDecimal baseTradeAmountUsdt,
        Integer maximumDailyNewPositions,
        String performanceWindowType,
        Integer performanceTradeCount,
        Integer performancePeriodDays,
        LocalDate performanceStartDate,
        LocalDate performanceEndDate,
        String dashboardIntervals,
        Boolean requireNewBuyTransition,
        Boolean dynamicProfitLockEnabled,
        BigDecimal profitLockActivationPercent,
        BigDecimal profitLockInitialPercent,
        BigDecimal profitLockTrailStepPercent
) {}
