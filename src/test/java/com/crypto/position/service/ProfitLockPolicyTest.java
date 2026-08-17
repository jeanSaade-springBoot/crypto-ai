package com.crypto.position.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class ProfitLockPolicyTest {
    private final ProfitLockPolicy policy = new ProfitLockPolicy();

    @Test
    void sharedPolicyActivatesAndNeverLoosensProtectedPrice() {
        var first = policy.evaluate(new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("108"),
                new BigDecimal("100"), false, null, true,
                new BigDecimal("70"), new BigDecimal("40"), new BigDecimal("10"));
        assertThat(first.active()).isTrue();
        assertThat(first.lockPrice()).isNotNull();
        var retrace = policy.evaluate(new BigDecimal("100"), new BigDecimal("110"), first.lockPrice(),
                first.highestPrice(), first.active(), first.lockPrice(), true,
                new BigDecimal("70"), new BigDecimal("40"), new BigDecimal("10"));
        assertThat(retrace.lockPrice()).isEqualByComparingTo(first.lockPrice());
        assertThat(retrace.triggered()).isTrue();
    }
}
