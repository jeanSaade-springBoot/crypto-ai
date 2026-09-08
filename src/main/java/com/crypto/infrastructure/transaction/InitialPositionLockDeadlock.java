package com.crypto.infrastructure.transaction;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** FIX-124: retry permission for a MySQL deadlock at the FIRST position lookup only.
 * No trading decision or wallet write has occurred at this point. Never wrap a
 * later failure with this marker: replaying a partially evaluated exit is unsafe.
 */
public final class InitialPositionLockDeadlock extends RuntimeException {
    public InitialPositionLockDeadlock(RuntimeException cause) { super(cause); }

    public static boolean isMysqlDeadlock(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1213
                    && "40001".equals(sql.getSQLState())) return true;
        }
        return false;
    }
}
