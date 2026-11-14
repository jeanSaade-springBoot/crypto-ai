package com.crypto.local;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.env.MockEnvironment;
import com.crypto.repository.CandleRepository;
import com.crypto.shared.SharedMarketSource;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Dedicated loopback MySQL only; missing environment fails, never silently skips.
 * Creates/drops only generated fix153_test_* schemas, after server UUID verification. */
@Timeout(30)
class LocalCandleMySqlIT extends LocalPriceEvidenceTest {
    JdbcTemplate admin;String schema;DriverManagerDataSource ds;
    @Override @BeforeEach void setup(){
        String url=System.getenv("FIX153_TEST_MYSQL_URL");
        if(url==null || !url.matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/fix153_test_[A-Za-z0-9_]+(?:\\?.*)?") || !"YES".equals(System.getenv("FIX153_TEST_MYSQL_DISPOSABLE")))throw new IllegalArgumentException("Dedicated disposable loopback fix153_test_ MySQL URL required");
        String user=System.getenv().getOrDefault("FIX153_TEST_MYSQL_USER","root"),password=System.getenv().getOrDefault("FIX153_TEST_MYSQL_PASSWORD","");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        String expected=System.getenv("FIX153_TEST_MYSQL_SERVER_UUID");
        if(expected==null || !expected.equals(admin.queryForObject("SELECT @@server_uuid",String.class)))throw new IllegalArgumentException("Dedicated test server UUID mismatch");
        schema="fix153_test_"+UUID.randomUUID().toString().replace("-","");admin.execute("CREATE DATABASE "+schema);
        String server=url.substring(0,url.indexOf('/',"jdbc:mysql://".length())+1);
        ds=new DriverManagerDataSource(server+schema+"?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",user,password);
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));tx.setIsolationLevel(2);prices=new com.crypto.market.service.MarketPriceEventService(jdbc);
        jdbc.execute("CREATE TABLE market_price_event(id BIGINT AUTO_INCREMENT PRIMARY KEY,symbol VARCHAR(32),observed_at TIMESTAMP(6),price DECIMAL(30,12),source VARCHAR(64),delivery_status VARCHAR(32),ordering_slot TINYINT,outcome_reason VARCHAR(64),created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),UNIQUE(symbol,observed_at,ordering_slot)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE effects(id INT PRIMARY KEY) ENGINE=InnoDB");observed=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }
    @AfterEach void drop(){if(schema!=null)admin.execute("DROP DATABASE "+schema);}
    CandleRepository candles(){
        jdbc.execute("CREATE TABLE candle(id BIGINT AUTO_INCREMENT PRIMARY KEY,symbol VARCHAR(32),interval_code VARCHAR(10),open_time TIMESTAMP(6),close_time TIMESTAMP(6),open_price DECIMAL(30,12),high_price DECIMAL(30,12),low_price DECIMAL(30,12),close_price DECIMAL(30,12),volume DECIMAL(38,12),quote_asset_volume DECIMAL(38,12),number_of_trades BIGINT,taker_buy_base_volume DECIMAL(38,12),taker_buy_quote_volume DECIMAL(38,12),closed BOOLEAN,ingestion_source VARCHAR(16),close_transition_token VARCHAR(36),created_at TIMESTAMP(6),updated_at TIMESTAMP(6),UNIQUE(symbol,interval_code,open_time)) ENGINE=InnoDB");
        return new CandleRepository(new SharedMarketSource(jdbc,new MockEnvironment()),jdbc);
    }
    CandleRepository.Transition runtime(CandleRepository repo,Instant open,boolean closed,String token,BigDecimal value){return tx.execute(t->repo.upsertRuntime("BTCUSDT","1m",open,open.plusSeconds(60).minusMillis(1),value,value,value,value,value,value,1L,value,value,closed,token));}
    @Test void closedTransitionTokenAndOhlcvAreImmutableAndBackfillCannotCreateRuntimeClose(){
        var repo=candles();Instant open=Instant.now().minusSeconds(180);
        assertFalse(runtime(repo,open,false,"open",BigDecimal.ONE).transitionedToClosed());
        assertTrue(runtime(repo,open,true,"first",BigDecimal.TEN).transitionedToClosed());
        assertFalse(runtime(repo,open,true,"duplicate",BigDecimal.ONE).transitionedToClosed());
        assertFalse(runtime(repo,open,false,"late",BigDecimal.ONE).transitionedToClosed());
        assertEquals(0,BigDecimal.TEN.compareTo(jdbc.queryForObject("SELECT close_price FROM candle",BigDecimal.class)));
        Instant backfill=open.minusSeconds(60);tx.executeWithoutResult(t->repo.upsert("BTCUSDT","1m",backfill,backfill.plusSeconds(59),price,price,price,price,price,price,1L,price,price,true));
        assertFalse(runtime(repo,backfill,true,"late-runtime",BigDecimal.ONE).transitionedToClosed());
        var restarted=new CandleRepository(new SharedMarketSource(jdbc,new MockEnvironment()),jdbc);
        assertFalse(runtime(restarted,backfill,true,"restart",BigDecimal.ONE).transitionedToClosed());
        assertEquals("BACKFILL",jdbc.queryForObject("SELECT ingestion_source FROM candle WHERE open_time=?",String.class,java.sql.Timestamp.from(backfill)));
    }
    @Test void concurrentCloseCallbacksHaveOneTransitionWinner()throws Exception{
        var repo=candles();Instant open=Instant.now().minusSeconds(180);
        runtime(repo,open,false,"open",BigDecimal.ONE);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var start=new java.util.concurrent.CountDownLatch(1);
            var first=pool.submit(()->{start.await();return runtime(repo,open,true,"a",BigDecimal.TEN);});
            var second=pool.submit(()->{start.await();return runtime(repo,open,true,"b",BigDecimal.ONE);});start.countDown();
            assertNotEquals(first.get(10,java.util.concurrent.TimeUnit.SECONDS).transitionedToClosed(),second.get(10,java.util.concurrent.TimeUnit.SECONDS).transitionedToClosed());
        }
    }
    @Test void authoritativeRecoveryIsUpdateOnlyAndGapBackfillIsInsertOnly(){
        var repo=candles();Instant open=Instant.now().minusSeconds(180).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        runtime(repo,open,false,"open",BigDecimal.ONE);
        assertTrue(Boolean.TRUE.equals(tx.execute(t->repo.finalizeExistingOpenFromAuthoritative("BTCUSDT","1m",open,open.plusSeconds(59),BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,1L,BigDecimal.TEN,BigDecimal.TEN))));
        assertFalse(Boolean.TRUE.equals(tx.execute(t->repo.finalizeExistingOpenFromAuthoritative("BTCUSDT","1m",open,open.plusSeconds(59),BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,1L,BigDecimal.ONE,BigDecimal.ONE))));
        assertEquals(0,BigDecimal.TEN.compareTo(jdbc.queryForObject("SELECT close_price FROM candle WHERE open_time=?",BigDecimal.class,java.sql.Timestamp.from(open))));
        Instant absent=open.minusSeconds(60);
        assertTrue(Boolean.TRUE.equals(tx.execute(t->repo.insertAuthoritativeGapBackfill("BTCUSDT","1m",absent,absent.plusSeconds(59),BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,1L,BigDecimal.TEN,BigDecimal.TEN))));
        assertFalse(Boolean.TRUE.equals(tx.execute(t->repo.insertAuthoritativeGapBackfill("BTCUSDT","1m",absent,absent.plusSeconds(59),BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,1L,BigDecimal.ONE,BigDecimal.ONE))));
        assertEquals("BACKFILL",jdbc.queryForObject("SELECT ingestion_source FROM candle WHERE open_time=?",String.class,java.sql.Timestamp.from(absent)));
        assertNull(jdbc.queryForObject("SELECT close_transition_token FROM candle WHERE open_time=?",String.class,java.sql.Timestamp.from(absent)));
    }
    @Test void resolverWaitsForEffectsCommitAndAppliedWins()throws Exception{
        long id=receipt();prices.release(id);jdbc.update("UPDATE market_price_event SET created_at=?",java.sql.Timestamp.from(Instant.now().minusSeconds(180)));
        var changed=new java.util.concurrent.CountDownLatch(1);var commit=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var effects=pool.submit(()->tx.executeWithoutResult(t->{jdbc.update("INSERT INTO effects VALUES(1)");prices.finish(id,"BTCUSDT",observed,price,"APPLIED","TEST");changed.countDown();try{assertTrue(commit.await(5,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}}));
            assertTrue(changed.await(5,java.util.concurrent.TimeUnit.SECONDS));var resolving=pool.submit(prices::resolvePending);
            commit.countDown();effects.get(10,java.util.concurrent.TimeUnit.SECONDS);resolving.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("APPLIED",state(id));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));
        }finally{commit.countDown();}
    }
    @Test void concurrentEqualTimestampHasExactlyOneCanonicalReceipt()throws Exception{
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var start=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{start.await();return tx.execute(t->prices.recordPending("BTCUSDT",BigDecimal.ONE,observed));});
            var b=pool.submit(()->{start.await();return tx.execute(t->prices.recordPending("BTCUSDT",BigDecimal.TEN,observed));});start.countDown();
            assertTrue((a.get(10,java.util.concurrent.TimeUnit.SECONDS)==null)!=(b.get(10,java.util.concurrent.TimeUnit.SECONDS)==null));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM market_price_event WHERE ordering_slot=1",Integer.class));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM market_price_event WHERE delivery_status='NON_CANONICAL'",Integer.class));
        }
    }
    @Test void freshFlywayBaselineUsesOnlyTraderSchema()throws Exception{
        // Minimal fixtures are removed from this freshly generated test schema only.
        jdbc.execute("DROP TABLE effects");jdbc.execute("DROP TABLE market_price_event");
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
        flyway.migrate();flyway.validate();
        assertEquals(1,jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version='106'",Integer.class));
        assertTrue(jdbc.queryForList("SHOW COLUMNS FROM candle LIKE 'ingestion_source'").size()==1);
        assertEquals(1,jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version='112'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='replay_position'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='replay_execution'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='replay_position_management'",Integer.class));
    }
}
