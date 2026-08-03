package com.crypto.wallet.dto;
import java.math.BigDecimal;
public record WalletSettingsRequest(BigDecimal baseTradeAmountUsdt, BigDecimal minimumUsdtReserve, boolean automaticExecutionEnabled) {}
