package com.crypto.shared;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** FIX-146: availability is distinct from permission. Keep diagnostics running,
 * but allow new shared work only for checked, approved symbols. No durable state
 * is changed by this registry and no stale check grants execution authority. */
public final class SharedTradingReadiness {
    public enum Reason { READY, NOT_CHECKED, ACTIVATION_REQUIRED, CUTOVER_REQUIRED, AUTOMATIC_ACTIVATION_WAITING,
        LEGACY_WORK_REVIEW, OWNER_INCONSISTENT, PARTIAL_DELIVERY, OWNERSHIP_MODE_MISMATCH,
        APPROVAL_AUDIT_MISSING, INVALID_BOUNDARY, SOURCE_REGRESSED, SOURCE_HISTORY_MISSING,
        UNSCOPED_WORK, CHECK_FAILED, CHECK_EXPIRED }
    public record Entry(Reason reason, Instant checkedAt) {}
    private final Supplier<Instant> clock;
    private final Map<String,Entry> entries=new ConcurrentHashMap<>();
    private volatile boolean managed;
    public SharedTradingReadiness() { this(Instant::now); }
    SharedTradingReadiness(Supplier<Instant> clock) { this.clock=clock; }
    public void begin() { managed=true; }
    public void retainSymbols(Collection<String> symbols) { entries.keySet().retainAll(symbols);
        for(String symbol:symbols)entries.putIfAbsent(symbol,new Entry(Reason.NOT_CHECKED,clock.get())); }
    public Entry record(String symbol,Reason reason) {
        return entries.put(symbol,new Entry(reason,clock.get()));
    }
    public boolean managed() {return managed;}
    public Reason reason(String symbol) {
        Entry entry=entries.get(symbol);
        if(entry==null)return Reason.NOT_CHECKED;
        if(!clock.get().isBefore(entry.checkedAt().plusSeconds(60)))return Reason.CHECK_EXPIRED;
        return entry.reason();
    }
    public boolean allowed(String symbol) {return !managed || reason(symbol)==Reason.READY;}
    public Map<String,Object> snapshot() {
        Instant now=clock.get();var ready=new ArrayList<String>();var blocked=new TreeMap<String,Object>();
        for(String symbol:new TreeSet<>(entries.keySet())) {
            Entry e=entries.get(symbol);if(e==null)continue;
            Reason reason=!now.isBefore(e.checkedAt().plusSeconds(60))?Reason.CHECK_EXPIRED:e.reason();
            if(reason==Reason.READY)ready.add(symbol);
            else blocked.put(symbol,Map.of("reason",reason.name(),"checkedUtc",e.checkedAt(),
                "checkedKsa",e.checkedAt().atOffset(ZoneOffset.ofHours(3))));
        }
        String status=!managed?"NOT_MANAGED":ready.isEmpty()?"NO_READY_SYMBOLS":blocked.isEmpty()?"READY":"DEGRADED";
        return Map.of("status",status,"managed",managed,"readySymbols",ready,"blockedSymbols",blocked,
            "observedUtc",now,"observedKsa",now.atOffset(ZoneOffset.ofHours(3)),"checkValiditySeconds",60);
    }
}
