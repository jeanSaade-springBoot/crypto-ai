package com.crypto.shared;

/** FIX-145: an audited operator disposition is not business completion or
 * rollback proof. Startup and isolated approval enforce the identical evidence.
 * Existing claim/due status allowlists continue to prevent replay. */
public final class OperatorWorkCancellation {
    private OperatorWorkCancellation() {}
    private static final String AUDITED_CANCELLED = """
(w.status='OPERATOR_CANCELLED' AND EXISTS (
              SELECT 1 FROM signal_processing_work_cancellation a
              WHERE a.signal_id=w.signal_id AND a.status='REVIEW_REQUIRED'
                AND a.source_event_id IS NULL AND a.owner_token IS NULL
                AND a.completed_at IS NULL AND a.paper_position_id IS NULL
                AND a.origin='WORKER' AND a.attempts=1
                AND a.decision='ABANDON_NO_REPLAY_EFFECTS_UNKNOWN'
                AND LENGTH(TRIM(a.operator_name))>0 AND LENGTH(TRIM(a.review_reference))>0
                AND w.symbol=a.symbol AND w.interval_code=a.interval_code
                AND w.candle_open_time=a.candle_open_time AND w.origin=a.origin
                AND w.attempts=a.attempts AND w.owner_token IS NULL
                AND w.completed_at IS NULL AND w.paper_position_id IS NULL
                AND w.failure_stage=a.failure_stage AND w.error_message=a.error_message
                AND w.next_attempt_at=a.next_attempt_at AND w.updated_at=a.cancelled_at
          ))
        """;
    public static final String UNATTRIBUTED_BLOCKERS = """
        SELECT w.signal_id,w.status FROM signal_processing_work w
        WHERE w.source_event_id IS NULL
          AND w.status NOT IN ('COMPLETED','EXPIRED','LOCK_RETRY_EXHAUSTED')
          AND NOT
        """ + AUDITED_CANCELLED + " ORDER BY w.signal_id LIMIT 25";
    // Approval still rejects attributed work and all other unfinished statuses.
    // The cancellation exemption is only for matching legacy NULL-source audits.
    public static final String APPROVAL_BLOCKERS = """
        SELECT w.signal_id,w.status,w.source_event_id FROM signal_processing_work w
        WHERE w.symbol=? AND w.status NOT IN ('COMPLETED','EXPIRED')
          AND NOT (w.source_event_id IS NULL AND
        """ + AUDITED_CANCELLED + ") ORDER BY w.signal_id LIMIT 25";
}
