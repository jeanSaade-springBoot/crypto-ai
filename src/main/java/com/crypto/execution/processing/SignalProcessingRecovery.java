package com.crypto.execution.processing;

import com.crypto.service.PaperTradingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** FIX-127: scans only registered work. Never discovers trades by backfilling
 * historic trade_signal rows. Explicit/startup work is never background executed. */
@Service
public class SignalProcessingRecovery {
    private static final Logger log=LoggerFactory.getLogger(SignalProcessingRecovery.class);
    private final SignalProcessingStore store;
    private final PaperTradingService paper;
    public SignalProcessingRecovery(SignalProcessingStore store,PaperTradingService paper) {this.store=store;this.paper=paper;}
    @Scheduled(fixedDelayString="${trading.fix127.recovery-delay-ms:30000}")
    public void recover() {
        try {
            int quarantined = store.quarantineInterrupted();
            if (quarantined > 0) log.warn("[FIX-127][INTERRUPTED_REVIEW] count={}; no automatic replay", quarantined);
        } catch(RuntimeException ex) {
            log.error("[FIX-128][QUARANTINE_SCAN_FAILED] pending recovery will still be attempted",ex);
        }
        // FIX-128: housekeeping failure must not suppress eligible pending work.
        try {
            for(long id:store.due()) {
                try { paper.recoverRegisteredSignal(id); }
                catch(RuntimeException ex) {log.error("[FIX-127][RECOVERY_FAILED] signalId={}",id,ex);}
            }
        } catch(RuntimeException ex) {log.error("[FIX-127][RECOVERY_SCAN_FAILED]",ex);}
    }
}
