package com.crypto.wallet.dto;

import java.math.BigDecimal;

public record WalletSettingsRequest(
        BigDecimal minimumUsdtReserve,
        Integer maximumDailyNewPositions,
        boolean automaticExecutionEnabled
) {}
