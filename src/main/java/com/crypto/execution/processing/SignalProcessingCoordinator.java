package com.crypto.execution.processing;

import com.crypto.domain.PaperPosition;
import com.crypto.domain.TradeSignal;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import com.crypto.infrastructure.transaction.InitialPositionLockDeadlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

/** FIX-127: one coordinator for all Production signal callers. No retry of the
 * existing body after it has begun; only the new initial lock is retry-marked. */
@Service
public class SignalProcessingCoordinator {
    private static final Logger log=LoggerFactory.getLogger(SignalProcessingCoordinator.class);
    private final SignalProcessingStore store;
    private final TradeSignalRepository signals;
    private final WalletManagedPositionRepository positions;
    private final SignalProcessingFreshness freshness;
    private final TransactionTemplate transaction;
    public SignalProcessingCoordinator(SignalProcessingStore store, TradeSignalRepository signals,
            WalletManagedPositionRepository positions, SignalProcessingFreshness freshness, PlatformTransactionManager manager) {
        this.store=store;this.signals=signals;this.positions=positions;this.freshness=freshness;
        transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Optional<PaperPosition> process(long signalId, boolean background,
            Function<TradeSignal,Optional<PaperPosition>> business) {
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("FIX-127 caller must be outside the processing transaction");
        if(!background) {
            // Automatic callers have already registered atomically with creation.
            // Explicit existing-signal calls register only at the user's request.
            transaction.executeWithoutResult(tx->store.register(signals.findById(signalId)
                    .orElseThrow(()->new IllegalArgumentException("Signal not found: "+signalId)),ProcessingOrigin.EXPLICIT));
        }
        for(int localAttempt=0;localAttempt<2;localAttempt++) {
            var work=store.claim(signalId,background);
            if(work==null) {
                log.info("[FIX-127][NOT_CLAIMED] signalId={}, background={}; no processing performed",signalId,background);
                return Optional.empty();
            }
            log.info("[FIX-127][ATTEMPT] signalId={}, symbol={}, interval={}, candleOpenTime={}, origin={}, attempt={}, background={}",
                    signalId,work.symbol(),work.interval(),work.candleOpenTime(),work.origin(),work.attempts(),background);
            try {
                Optional<PaperPosition> result=transaction.execute(tx->{
                    var owned=store.locked(signalId);
                    if(owned==null || !"RUNNING".equals(owned.status()) || !work.owner().equals(owned.owner()))
                        throw new IllegalStateException("FIX-127 processing ownership changed");
                    // Acquire before PositionManagementService inserts a referencing
                    // advisory or executes POSITION_STOP_LOSS. No rules evaluated yet.
                    try {
                        positions.findFirstBySymbolAndStatusOrderByOpenedAtDesc(work.symbol(),"OPEN");
                    } catch(RuntimeException ex) {
                        if(InitialPositionLockDeadlock.isMysqlDeadlock(ex))throw new InitialLockFailure(ex);
                        throw ex;
                    }
                    // Read signal/freshness after lock acquisition: lock wait must not
                    // consume the recovery grace before it is checked, or establish an
                    // ordinary repeatable-read snapshot before the position is locked.
                    TradeSignal signal=signals.findById(signalId).orElseThrow(()->new IllegalArgumentException("Signal missing"));
                    if(!work.symbol().equals(signal.getSymbol()) || !work.interval().equals(signal.getInterval())
                            || !work.candleOpenTime().equals(signal.getCandleOpenTime()))
                        throw new IllegalStateException("FIX-127 signal lineage changed");
                    if((background || work.attempts()>1) && !freshness.eligible(work,Instant.now()))throw new Expired();
                    Optional<PaperPosition> outcome=business.apply(signal);
                    store.complete(work,outcome.map(PaperPosition::getId).orElse(null));
                    return outcome;
                });
                log.info("[FIX-127][COMPLETED] signalId={}, symbol={}, interval={}, attempt={}; processing committed (not necessarily a trade)",
                        signalId,work.symbol(),work.interval(),work.attempts());
                return result;
            } catch(Expired ex) {
                store.finishWithoutBusiness(work,"EXPIRED","FRESHNESS","Exact candle is no longer latest and fresh for recovery");
                log.warn("[FIX-127][EXPIRED] signalId={}, symbol={}, attempt={}",signalId,work.symbol(),work.attempts());
                return Optional.empty();
            } catch(RuntimeException ex) {
                boolean initial=ex instanceof InitialLockFailure;
                String status=initial && work.attempts()<2 ? "RETRYABLE_FAILURE" : "REVIEW_REQUIRED";
                int recorded=store.finishWithoutBusiness(work,status,initial ? "INITIAL_POSITION_LOCK" : "PROCESSING_OR_COMMIT",ex.toString());
                log.error("[FIX-127][{}] signalId={}, symbol={}, interval={}, attempt={}, initialLock={}",
                        recorded==1 ? status : "FAILURE_RECORD_NOT_UPDATED",signalId,work.symbol(),work.interval(),work.attempts(),initial,ex);
                if(!initial || work.attempts()>=2)throw ex;
                // TransactionTemplate completed rollback before entering this catch.
            }
        }
        return Optional.empty();
    }
    // A private marker cannot be supplied by a later business callback.
    private static final class InitialLockFailure extends RuntimeException {
        InitialLockFailure(RuntimeException cause) { super(cause); }
    }
    private static final class Expired extends RuntimeException {}
}
