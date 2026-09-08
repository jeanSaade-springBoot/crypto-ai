package com.crypto.infrastructure.transaction;

import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class InitialPositionLockDeadlockTest {
    @Test void onlyMysql1213With40001IsRetryable() {
        assertTrue(InitialPositionLockDeadlock.isMysqlDeadlock(new RuntimeException(new SQLException("deadlock","40001",1213))));
        assertFalse(InitialPositionLockDeadlock.isMysqlDeadlock(new SQLException("timeout","HY000",1205)));
        assertFalse(InitialPositionLockDeadlock.isMysqlDeadlock(new SQLException("message too long","22001",1406)));
        assertFalse(InitialPositionLockDeadlock.isMysqlDeadlock(new SQLException("serialization","40001",0)));
        assertFalse(InitialPositionLockDeadlock.isMysqlDeadlock(new IllegalStateException("Deadlock found")));
    }
}
