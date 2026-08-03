package com.crypto.wallet.dto;
import java.math.BigDecimal;
public record WalletCashFlowRequest(String flowType, BigDecimal amountUsdt, String notes) {}
