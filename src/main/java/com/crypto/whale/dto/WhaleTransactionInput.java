package com.crypto.whale.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WhaleTransactionInput(
        String blockchain,
        String transactionHash,
        String fromAddress,
        String toAddress,
        String fromLabel,
        String toLabel,
        String asset,
        BigDecimal amount,
        BigDecimal usdValue,
        Instant observedAt
) {}
