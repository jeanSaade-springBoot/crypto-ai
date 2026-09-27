package com.crypto.wallet.service;

import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

/** FIX-125: symbol lock precedes position reads; mutation lock replaces the
 * method-body monitor and survives the actual OUTER commit/rollback. No lease,
 * after-commit unlock SQL or business retry is used. FIX-134 joins all asset writers.
 * Mutation serialization is intentionally wallet-wide, matching the former global
 * automatic monitor but extending through commit and covering manual writers.
 * Pure signal/protection evaluation does NOT take the mutation lock. */
@Service
public class WalletTransactionCoordination {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(WalletTransactionCoordination.class);
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private io.micrometer.core.instrument.MeterRegistry meters;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate provisioning;
    public WalletTransactionCoordination(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc=jdbc; provisioning=new TransactionTemplate(manager);
        provisioning.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        provisioning.setTimeout(3);
    }
    /** Provision before enabling/entering trading, never after holding a position. */
    public void provision(String symbol) {
        String key=normalize(symbol);
        provisioning.executeWithoutResult(tx->jdbc.update("INSERT IGNORE INTO wallet_symbol_coordination(symbol) VALUES(?)",key));
    }
    // Two-second JDBC statement cancellation bound, not a total transaction deadline.
    // No pooled SESSION variables are changed. Cancellation propagates to the owner,
    // which rolls back the whole transaction; uncertain business work is never retried.
    public void symbol(String symbol) {
        requireTransaction();String key=normalize(symbol);
        if(TransactionSynchronizationManager.getSynchronizations().stream().anyMatch(s->s instanceof SymbolOwnership owned && owned.symbol.equals(key)))return;
        if(mutationHeld())throw new IllegalStateException("[FIX-125][LOCK_ORDER] cannot acquire new symbol after wallet mutation guard");
        long start=System.nanoTime();
        var rows=jdbc.query(connection->{
            var statement=connection.prepareStatement("SELECT symbol FROM wallet_symbol_coordination WHERE symbol=? FOR UPDATE");
            statement.setQueryTimeout(2);statement.setString(1,key);return statement;
        },(rs,n)->rs.getString(1));
        if(rows.size()!=1)throw new IllegalStateException("[FIX-125][UNPROVISIONED_SYMBOL] "+key);
        TransactionSynchronizationManager.registerSynchronization(new SymbolOwnership(key));
        timing("SYMBOL",key,start);
    }
    public void mutation() {
        requireTransaction();
        if(mutationHeld())return; // Re-entrant within this transaction only, never across REQUIRES_NEW.
        long start=System.nanoTime();
        var ids=jdbc.query(connection->{
            var statement=connection.prepareStatement("SELECT id FROM wallet_mutation_coordination WHERE id=1 FOR UPDATE");
            statement.setQueryTimeout(2);return statement;
        },(rs,n)->rs.getInt(1));
        if(ids.size()!=1)throw new IllegalStateException("FIX-125 mutation guard missing");
        TransactionSynchronizationManager.registerSynchronization(new MutationOwnership());
        timing("MUTATION","WALLET",start);
    }
    /** Synchronizations are suspended by Spring with their owning transaction.
     * A plain ThreadLocal would incorrectly authorize a nested REQUIRES_NEW writer. */
    private static final class SymbolOwnership implements TransactionSynchronization {
        private final String symbol;
        private SymbolOwnership(String symbol){this.symbol=symbol;}
    }
    private static final class MutationOwnership implements TransactionSynchronization {}
    public static boolean mutationHeld() {
        return TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()
            && TransactionSynchronizationManager.getSynchronizations().stream().anyMatch(MutationOwnership.class::isInstance);
    }
    public static void requireMutation() {
        if(!mutationHeld())throw new IllegalStateException("[FIX-125][MUTATION_REQUIRED] wallet repository access without transaction-owned guard");
    }
    private void timing(String stage,String symbol,long start) {
        long acquired=System.nanoTime();
        long wait=(acquired-start)/1_000_000;
        if(meters!=null)io.micrometer.core.instrument.Timer.builder("wallet.coordination.wait")
            .tag("stage",stage).publishPercentileHistogram().register(meters)
            .record(acquired-start,java.util.concurrent.TimeUnit.NANOSECONDS);
        if(wait>=100)log.warn("[FIX-125][LOCK_WAIT] stage={}, symbol={}, waitMs={}",stage,symbol,wait);
        else log.debug("[FIX-125][LOCK_ACQUIRED] stage={}, symbol={}, waitMs={}",stage,symbol,wait);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status) {
                long completed=System.nanoTime();
                long elapsed=(completed-start)/1_000_000;
                if(meters!=null)io.micrometer.core.instrument.Timer.builder("wallet.coordination.hold")
                    .tag("stage",stage).tag("outcome",status==STATUS_COMMITTED?"COMMITTED":"ROLLED_BACK")
                    .publishPercentileHistogram().register(meters)
                    .record(completed-acquired,java.util.concurrent.TimeUnit.NANOSECONDS);
                if(status!=STATUS_COMMITTED || wait>=100 || elapsed>=1000)
                    log.info("[FIX-125][TX_COMPLETED] stage={}, symbol={}, outcome={}, waitMs={}, holdMs={}, elapsedMs={}",stage,symbol,status==STATUS_COMMITTED?"COMMITTED":"ROLLED_BACK",wait,(System.nanoTime()-acquired)/1_000_000,elapsed);
                else log.debug("[FIX-125][TX_COMPLETED] stage={}, symbol={}, outcome=COMMITTED, elapsedMs={}",stage,symbol,elapsed);
            }
        });
    }
    private static void requireTransaction() {
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("FIX-125 requires owning transaction");
    }
    private static String normalize(String symbol) {
        String key=symbol==null?"":symbol.trim().toUpperCase(Locale.ROOT);
        if(!key.matches("[A-Z0-9]{1,26}USDT"))throw new IllegalArgumentException("FIX-125 invalid symbol");
        return key;
    }
}
