package com.crypto.wallet.dto;

import java.math.BigDecimal;

public record WalletSettingsRequest(
        BigDecimal minimumUsdtReserve,
        BigDecimal baseTradeAmountUsdt,
        Integer maximumDailyNewPositions
) {}
