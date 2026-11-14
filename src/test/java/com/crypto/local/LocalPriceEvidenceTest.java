package com.crypto.local;

import com.crypto.market.service.MarketPriceEventService;
import com.crypto.position.service.LivePositionProtectionService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real transactions certify price evidence atomicity; MySQL row races have a separate opt-in gate. */
class LocalPriceEvidenceTest {
    JdbcTemplate jdbc;TransactionTemplate tx;MarketPriceEventService prices;Instant observed;final BigDecimal price=new BigDecimal("10");
    @BeforeEach void setup(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));prices=new MarketPriceEventService(jdbc);
        jdbc.execute("CREATE TABLE market_price_event(id BIGINT AUTO_INCREMENT PRIMARY KEY,symbol VARCHAR(32),observed_at TIMESTAMP(6),price DECIMAL(30,12),source VARCHAR(64),delivery_status VARCHAR(32),ordering_slot TINYINT,outcome_reason VARCHAR(64),created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP,UNIQUE(symbol,observed_at,ordering_slot))");
        jdbc.execute("CREATE TABLE effects(id INT PRIMARY KEY)");observed=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }
    long receipt(){return tx.execute(t->prices.recordPending("BTCUSDT",price,observed));}
    String state(long id){return jdbc.queryForObject("SELECT delivery_status FROM market_price_event WHERE id=?",String.class,id);}
    @Test void effectsAndAppliedCommitOrRollbackTogetherAndPendingBlocksReplay(){
        long id=receipt();assertThrows(IllegalStateException.class,()->prices.assertReplayEvidence("BTCUSDT",observed.minusSeconds(1),observed.plusSeconds(1)));
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(t->{jdbc.update("INSERT INTO effects VALUES(1)");prices.finish(id,"BTCUSDT",observed,price,"APPLIED","TEST");throw new IllegalStateException("abort");}));
        assertEquals("PENDING",state(id));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));
        tx.executeWithoutResult(t->{jdbc.update("INSERT INTO effects VALUES(1)");prices.finish(id,"BTCUSDT",observed,price,"APPLIED","TEST");});
        assertEquals("APPLIED",state(id));prices.assertReplayEvidence("BTCUSDT",observed.minusSeconds(1),observed.plusSeconds(1));assertEquals(1,prices.find("BTCUSDT",observed.minusSeconds(1),observed.plusSeconds(1)).size());
    }
    @Test void afterCommitAcknowledgmentFailureKeepsAppliedAndResolverCannotEraseIt(){
        long id=receipt();prices.release(id);
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(t->{prices.finish(id,"BTCUSDT",observed,price,"APPLIED","TEST");TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCommit(){throw new IllegalStateException("lost ack");}});}));
        jdbc.update("UPDATE market_price_event SET created_at=?",Timestamp.from(Instant.now().minusSeconds(180)));
        prices.resolvePending();assertEquals("APPLIED",state(id));
    }
    @Test void resolverWinsThenLateEffectsCannotCommit(){
        long id=receipt();prices.release(id);jdbc.update("UPDATE market_price_event SET created_at=?",Timestamp.from(Instant.now().minusSeconds(180)));
        prices.resolvePending();assertEquals("PROTECTION_NOT_APPLIED",state(id));
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(t->{jdbc.update("INSERT INTO effects VALUES(1)");prices.finish(id,"BTCUSDT",observed,price,"APPLIED","TEST");}));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));assertTrue(prices.find("BTCUSDT",observed.minusSeconds(1),observed.plusSeconds(1)).isEmpty());
    }
    @Test void firstValidTimestampKeepsSlotAfterRejectionAndUnequalDuplicateCannotApply(){
        long id=receipt();tx.executeWithoutResult(t->prices.finish(id,"BTCUSDT",observed,price,"NON_CANONICAL","STALE_PRICE"));
        assertNull(tx.execute(t->prices.recordPending("BTCUSDT",new BigDecimal("11"),observed)));
        assertEquals(1,jdbc.queryForObject("SELECT ordering_slot FROM market_price_event WHERE id=?",Integer.class,id));
        assertEquals("AMBIGUOUS_EQUAL_TIMESTAMP",jdbc.queryForObject("SELECT outcome_reason FROM market_price_event WHERE id<>?",String.class,id));
        assertFalse(prices.laterApplied("BTCUSDT",observed.minusSeconds(1)),"rejected/PENDING rows do not create ordering authority");
    }
    @Test void noPositionStillCommitsAppliedAndFreshnessIsCheckedAfterLock(){
        var managed=mock(com.crypto.wallet.repository.WalletManagedPositionRepository.class);
        var service=new LivePositionProtectionService(managed,null,null,null,null,null,null,null,null,null);
        ReflectionTestUtils.setField(service,"priceEvents",prices);when(managed.findFirstBySymbolAndStatusOrderByOpenedAtDesc("BTCUSDT","OPEN")).thenReturn(Optional.empty());
        long id=receipt();tx.executeWithoutResult(t->service.onPrice("BTCUSDT",price,observed,id));assertEquals("APPLIED",state(id));
        observed=observed.plusMillis(1);long second=receipt();
        doAnswer(inv->{ReflectionTestUtils.setField(prices,"maxAge",-1L);return Optional.empty();}).when(managed).findFirstBySymbolAndStatusOrderByOpenedAtDesc("BTCUSDT","OPEN");
        tx.executeWithoutResult(t->service.onPrice("BTCUSDT",price,observed,second));assertEquals("NON_CANONICAL",state(second));
        assertEquals("STALE_PRICE",jdbc.queryForObject("SELECT outcome_reason FROM market_price_event WHERE id=?",String.class,second));
    }
    @Test void increasingObservationsApplyAndAnOlderFreshObservationIsRejected(){
        var managed=mock(com.crypto.wallet.repository.WalletManagedPositionRepository.class);
        when(managed.findFirstBySymbolAndStatusOrderByOpenedAtDesc("BTCUSDT","OPEN")).thenReturn(Optional.empty());
        var service=new LivePositionProtectionService(managed,null,null,null,null,null,null,null,null,null);
        ReflectionTestUtils.setField(service,"priceEvents",prices);
        for(int n=0;n<5;n++){
            observed=observed.plusMillis(1);long id=receipt();
            tx.executeWithoutResult(t->service.onPrice("BTCUSDT",price,observed,id));assertEquals("APPLIED",state(id));
        }
        observed=observed.minusMillis(10);long old=receipt();tx.executeWithoutResult(t->service.onPrice("BTCUSDT",price,observed,old));
        assertEquals("NON_CANONICAL",state(old));assertEquals("OUT_OF_ORDER",jdbc.queryForObject("SELECT outcome_reason FROM market_price_event WHERE id=?",String.class,old));
    }
    @Test void initialPositionDeadlockRetainsExistingPreBusinessRetryClassification(){
        var managed=mock(com.crypto.wallet.repository.WalletManagedPositionRepository.class);
        when(managed.findFirstBySymbolAndStatusOrderByOpenedAtDesc("BTCUSDT","OPEN"))
            .thenThrow(new RuntimeException(new java.sql.SQLException("controlled lock conflict","40001",1213)));
        var service=new LivePositionProtectionService(managed,null,null,null,null,null,null,null,null,null);
        ReflectionTestUtils.setField(service,"priceEvents",prices);long id=receipt();
        assertThrows(com.crypto.infrastructure.transaction.InitialPositionLockDeadlock.class,
            ()->tx.executeWithoutResult(t->service.onPrice("BTCUSDT",price,observed,id)));
        assertEquals("PENDING",state(id));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));
    }
    @Test void invalidTimestampIsNotReplacedByReceiptTime(){
        assertNull(tx.execute(t->prices.recordPending("BTCUSDT",price,null)));
        assertNull(tx.execute(t->prices.recordPending("BTCUSDT",price,Instant.now().minusSeconds(16))));
        assertNull(tx.execute(t->prices.recordPending("BTCUSDT",price,Instant.now().plusSeconds(3))));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM market_price_event",Integer.class));
    }
}
