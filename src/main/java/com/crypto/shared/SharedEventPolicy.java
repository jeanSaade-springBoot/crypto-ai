package com.crypto.shared;

import java.time.*;

/** FIX-132 explicitly approved eligibility gates; no scoring/sizing changes. */
public final class SharedEventPolicy {
    private SharedEventPolicy() {}
    public static boolean priceEligible(String source,String classification,Instant observed,Instant previous,Instant now) {
        return "ELIGIBLE".equals(priceReason(source,classification,observed,previous,now));
    }
    /** Persist the precise exclusion; sequence advancement never grants live authority. */
    public static String priceReason(String source,String classification,Instant observed,Instant previous,Instant now) {
        if (!"LIVE_WEBSOCKET".equals(source)) return "NON_LIVE_SOURCE";
        if (!"LIVE".equals(classification)) return "CLASSIFICATION_" + classification;
        if (observed == null) return "UNKNOWN_OBSERVATION_TIME";
        if (observed.isAfter(now)) return "FUTURE_OBSERVATION";
        if (observed.plusSeconds(15).isBefore(now)) return "STALE_PRICE";
        if (previous != null && observed.isBefore(previous)) return "REGRESSED_OBSERVATION";
        return "ELIGIBLE";
    }

    public static boolean closeEligible(String interval,Instant close,Instant now) {
        return close!=null && !close.isAfter(now) && !close.plusSeconds(closeGraceSeconds(interval)).isBefore(now);
    }
    public static long closeGraceSeconds(String interval) {
        return switch(interval) { case "1m"->90;case "5m"->120;case "1h"->300;case "4h"->600;case "1d"->1800;default->120; };
    }
}
