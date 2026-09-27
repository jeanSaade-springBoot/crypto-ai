package com.crypto.shared;

import java.util.Map;
import java.util.Objects;

/** Approval is deliberately separate from operational readiness. Observation can
 * advance checkpoints, but can never manufacture execution authority. */
public final class SharedCutoverPolicy {
    private SharedCutoverPolicy() {}
    public static boolean approved(Map<String,Object> row) {
        return "READY".equals(row.get("status"))
            && "OPERATOR_APPROVED".equals(row.get("cutover_source"))
            && text(row.get("approved_by")) && text(row.get("approval_reference"))
            && row.get("approved_at") != null && row.get("approved_sequence") instanceof Number
            && row.get("cutover_sequence") instanceof Number
            && ((Number)row.get("approved_sequence")).longValue()==((Number)row.get("cutover_sequence")).longValue()
            && row.get("approved_cutover_at") != null
            && Objects.equals(row.get("approved_cutover_at"),row.get("cutover_at"));
    }
    public static boolean admitted(Map<String,Object> row, boolean live) {
        if(live)return approved(row);
        return "PENDING_CUTOVER".equals(row.get("status")) || "READY".equals(row.get("status"));
    }
    private static boolean text(Object value) { return value instanceof String s && !s.isBlank(); }
}
