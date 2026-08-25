package com.crypto.execution.domain;

/** FIX-091 / Fix 5: auditable result of transition/exposure authority evaluation. */
public record EntryAuthorityDecision(
        EntryAuthority authority,
        int maxPositionPercent,
        boolean structuralCandidate,
        boolean safetyComplete,
        String code,
        String explanation
) {
    public static EntryAuthorityDecision normal() {
        return new EntryAuthorityDecision(EntryAuthority.NORMAL_ENTRY, 100, false, true,
                "NORMAL_ENTRY_AUTHORITY", "Normal strategy authority applies.");
    }
}
