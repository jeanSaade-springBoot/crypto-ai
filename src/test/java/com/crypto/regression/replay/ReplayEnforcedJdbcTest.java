package com.crypto.regression.replay;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ReplayEnforcedJdbcTest {
    @Test void rejectsForbiddenTableBeforeJdbcCanRun() {
        ReplayEnforcedJdbc access = new ReplayEnforcedJdbc(new JdbcTemplate());
        assertThrows(ReplayForbiddenDependencyException.class,
                () -> access.rows("wallet_trade", ReplayAccessPolicy.Operation.REPLAY_READ, "SELECT 1"));
    }
}
