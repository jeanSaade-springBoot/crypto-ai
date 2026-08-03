package com.crypto.wallet.dto;
import java.math.BigDecimal;
public record WalletTradeRequest(Long signalId, String symbol, String side, BigDecimal quantity,
                                 BigDecimal priceUsdt, BigDecimal feeUsdt, String executionType, String notes) {}
