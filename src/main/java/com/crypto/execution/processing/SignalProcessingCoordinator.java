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
    // FIX-125 mandatory in Spring; null only in existing constructor-only policy tests.
    @org.springframework.beans.factory.annotation.Autowired
    private com.crypto.wallet.service.WalletTransactionCoordination walletCoordination;

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
        return process(signalId,background,business,null);
    }
    public Optional<PaperPosition> process(long signalId, boolean background,
            Function<TradeSignal,Optional<PaperPosition>> business, Long sourceEventId) {
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("FIX-127 caller must be outside the processing transaction");
        if(!background && !store.exists(signalId)) {
            // Automatic callers have already registered atomically with creation.
            // Explicit existing-signal calls register only at the user's request.
            transaction.executeWithoutResult(tx->store.register(signals.findById(signalId)
                    .orElseThrow(()->new IllegalArgumentException("Signal not found: "+signalId)),ProcessingOrigin.EXPLICIT));
        }
        store.requireSourceOwner(signalId,sourceEventId);
        for(int localAttempt=0;localAttempt<2;localAttempt++) {
            var work=store.claim(signalId,background);
            if(work==null) {
                log.info("[FIX-127][NOT_CLAIMED] signalId={}, background={}; no processing performed",signalId,background);
                return Optional.empty();
            }
            log.info("[FIX-127][ATTEMPT] signalId={}, symbol={}, interval={}, candleOpenTime={}, origin={}, attempt={}, background={}",
                    signalId,work.symbol(),work.interval(),work.candleOpenTime(),work.origin(),work.attempts(),background);
            java.util.concurrent.atomic.AtomicInteger completion=new java.util.concurrent.atomic.AtomicInteger(-1);
            try {
                Optional<PaperPosition> result=transaction.execute(tx->{
                    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override public void afterCompletion(int status) { completion.set(status); }
                        });
                    // FIX-140: observe the real transaction completion; a thrown exception alone
                    // does not prove rollback. The private marker is only created here.
                    if(walletCoordination!=null) {
                        try { walletCoordination.symbol(work.symbol()); }
                        catch(RuntimeException failure) {
                            if(sourceEventId!=null && retryableSymbolLock(failure))throw new SymbolLockFailure(failure);
                            throw failure;
                        }
                    }
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
                    store.requireSourceOwner(signalId,sourceEventId);
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
                // FIX-140: only this invocation's initial symbol-lock marker plus a
                // confirmed rollback grants retry. Unknown connection/commit failures stay review-only.
                if(ex instanceof SymbolLockFailure && completion.get()==
                        org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK) {
                    String result=work.attempts()<4?"SYMBOL_LOCK_RETRY":"LOCK_RETRY_EXHAUSTED";
                    int changed=store.finishSymbolLockFailure(work,result,ex.toString());
                    if(changed!=1)throw new IllegalStateException("FIX-140 retry persistence unconfirmed",ex);
                    log.warn("[FIX-140][SAFE_RETRY_RESULT] event={}, signal={}, attempt={}, outcome={}, rollback=CONFIRMED",
                        sourceEventId,signalId,work.attempts(),result);
                    if("SYMBOL_LOCK_RETRY".equals(result))throw new SymbolLockRetryScheduled(signalId);
                    return Optional.empty();
                }
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
    // FIX-140: classify only known lock cancellation/deadlock errors. Communications
    // errors are deliberately excluded even when nested below a data-access exception.
    static boolean retryableSymbolLock(Throwable failure) {
        for(Throwable e=failure;e!=null;e=e.getCause())
            if(e instanceof java.sql.SQLException sql && sql.getSQLState()!=null && sql.getSQLState().startsWith("08"))return false;
        for(Throwable e=failure;e!=null;e=e.getCause())
            if(e instanceof org.springframework.dao.QueryTimeoutException ||
               e instanceof java.sql.SQLTimeoutException ||
               e instanceof java.sql.SQLException sql && (sql.getErrorCode()==1205 || sql.getErrorCode()==1213))return true;
        return false;
    }
    private static final class SymbolLockFailure extends RuntimeException {
        SymbolLockFailure(Throwable cause) { super(cause); }
    }
    // A private marker cannot be supplied by a later business callback.
    private static final class InitialLockFailure extends RuntimeException {
        InitialLockFailure(RuntimeException cause) { super(cause); }
    }
    private static final class Expired extends RuntimeException {}
}
