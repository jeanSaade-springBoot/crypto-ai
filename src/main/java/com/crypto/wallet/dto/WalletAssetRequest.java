package com.crypto.wallet.dto;
import java.math.BigDecimal;
public record WalletAssetRequest(String symbol, BigDecimal quantity, BigDecimal averageBuyPriceUsdt) {}
