package com.crypto.wallet.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class WalletExecutionSizingPolicyTest {
    private final WalletExecutionSizingPolicy policy = new WalletExecutionSizingPolicy();

    @Test
    void productionAndReplayCanUseIdenticalBudgetReserveAndAllocationMath() {
        BigDecimal budget = policy.initialDailyBudget(new BigDecimal("10000"), new BigDecimal("100"),
                new BigDecimal("500"), 0);
        assertThat(budget).isEqualByComparingTo("500");
        var probe = policy.plan(new BigDecimal("10000"), new BigDecimal("100"), budget,
                15, 0, true, 0, 0, new BigDecimal("0.00000445"));
        assertThat(probe.allowed()).isTrue();
        assertThat(probe.normalizedPositionPercent()).isEqualTo(15);
        assertThat(probe.spend()).isEqualByComparingTo("75");
    }

    @Test
    void addIsCappedByRemainingAllocationExactlyLikeProduction() {
        var add = policy.plan(new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("500"),
                40, 80, false, 0, 0, new BigDecimal("100"));
        assertThat(add.allowed()).isTrue();
        assertThat(add.normalizedPositionPercent()).isEqualTo(20);
        assertThat(add.spend()).isEqualByComparingTo("100");
    }
}
