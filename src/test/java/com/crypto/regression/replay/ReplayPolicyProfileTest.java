package com.crypto.regression.replay;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReplayPolicyProfileTest {

    @Test
    void acceptsCompleteFrozenProfile() {
        // FIX-156: Replay requires an immutable historical profile rather than wallet_settings.
        assertDoesNotThrow(() -> new ReplayPolicyProfile(1L, "fixture-hash", Instant.EPOCH,
                "policy-fingerprint", "PRODUCTION_LOGIC_SIMULATION", true,
                BigDecimal.TEN, BigDecimal.valueOf(25), 3,
                "exchange-fixture", BigDecimal.TEN,
                true, BigDecimal.valueOf(70), BigDecimal.valueOf(40), BigDecimal.TEN,
                BigDecimal.valueOf(25)));
    }

    @Test
    void refusesIncompleteFrozenProfile() {
        // FIX-156 / E10: no current configuration fallback is allowed.
        assertThrows(IllegalArgumentException.class, () -> new ReplayPolicyProfile(1L, null, Instant.EPOCH,
                "policy-fingerprint", "PRODUCTION_LOGIC_SIMULATION", true,
                BigDecimal.TEN, BigDecimal.valueOf(25), 3,
                "exchange-fixture", BigDecimal.TEN,
                true, BigDecimal.valueOf(70), BigDecimal.valueOf(40), BigDecimal.TEN,
                BigDecimal.valueOf(25)));
        assertThrows(IllegalArgumentException.class, () -> new ReplayPolicyProfile(1L, "fixture-hash", null,
                "policy-fingerprint", "PRODUCTION_LOGIC_SIMULATION", true,
                BigDecimal.TEN, BigDecimal.valueOf(25), 3,
                "exchange-fixture", BigDecimal.TEN,
                true, BigDecimal.valueOf(70), BigDecimal.valueOf(40), BigDecimal.TEN,
                BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ReplayPolicyProfile(1L, "fixture-hash", Instant.EPOCH,
                null, "PRODUCTION_LOGIC_SIMULATION", true,
                BigDecimal.TEN, BigDecimal.valueOf(25), 3,
                "exchange-fixture", BigDecimal.TEN,
                true, BigDecimal.valueOf(70), BigDecimal.valueOf(40), BigDecimal.TEN,
                BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new ReplayPolicyProfile(1L, "fixture-hash", Instant.EPOCH,
                "policy-fingerprint", null, true,
                BigDecimal.TEN, BigDecimal.valueOf(25), 3,
                "exchange-fixture", BigDecimal.TEN,
                true, null, BigDecimal.valueOf(40), BigDecimal.TEN, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new ReplayPolicyProfile(1L, "fixture-hash", Instant.EPOCH,
                "policy-fingerprint", "PRODUCTION_LOGIC_SIMULATION", true,
                null, BigDecimal.valueOf(25), 3,
                "exchange-fixture", BigDecimal.TEN,
                true, BigDecimal.valueOf(70), BigDecimal.valueOf(40), BigDecimal.TEN, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new ReplayPolicyProfile(1L, "fixture-hash", Instant.EPOCH,
                "policy-fingerprint", "PRODUCTION_LOGIC_SIMULATION", true,
                BigDecimal.TEN, BigDecimal.valueOf(25), 3,
                null, BigDecimal.TEN,
                true, BigDecimal.valueOf(70), BigDecimal.valueOf(40), BigDecimal.TEN, BigDecimal.ONE));
    }
}
