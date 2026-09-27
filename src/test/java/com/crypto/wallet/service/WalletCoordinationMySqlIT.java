package com.crypto.wallet.service;

import com.crypto.domain.TradeSignal;
import com.crypto.wallet.domain.*;
import com.crypto.wallet.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real JPA repositories and wallet arithmetic on isolated MySQL. Portfolio
 * valuation/snapshot collaborator is mocked: this does not claim full signal-path parity. */
@org.junit.jupiter.api.Timeout(60)
class WalletCoordinationMySqlIT {
    JdbcTemplate admin,jdbc; String schema; LocalContainerEntityManagerFactoryBean factory;
    EntityManager em; TransactionTemplate tx; WalletTransactionCoordination guard;
    WalletAutoExecutionService wallet; WalletAssetRepository assets; WalletManagedPositionRepository positions;
    WalletTradeRepository trades;
    com.zaxxer.hikari.HikariDataSource pool;
    JpaTransactionManager manager; JpaRepositoryFactory repos;

    @BeforeEach void setup() throws Exception {
        String url=System.getenv("FIX132_TEST_MYSQL_URL");
        if(url==null || !url.matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/fix132_test_[A-Za-z0-9_]+(?:\\?.*)?"))throw new IllegalArgumentException("Dedicated loopback test MySQL required");
        String user=System.getenv().getOrDefault("FIX132_TEST_MYSQL_USER","root"),password=System.getenv().getOrDefault("FIX132_TEST_MYSQL_PASSWORD","");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        schema="fix132_test_wallet_"+UUID.randomUUID().toString().replace("-","");admin.execute("CREATE DATABASE "+schema);
        String server=url.substring(0,url.indexOf('/',"jdbc:mysql://".length())+1);
        var ds=new DriverManagerDataSource(server+schema+"?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",user,password);
        pool=new com.zaxxer.hikari.HikariDataSource();pool.setJdbcUrl(ds.getUrl());pool.setUsername(user);pool.setPassword(password);pool.setMaximumPoolSize(5);pool.setMinimumIdle(0);pool.setConnectionTimeout(3000);
        jdbc=new JdbcTemplate(pool);
        factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(pool);
        factory.setManagedTypes(org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes.of(
            "com.crypto.domain.TradeSignal","com.crypto.domain.PaperPosition","com.crypto.position.domain.PositionAnalysis",
            "com.crypto.wallet.domain.WalletAsset","com.crypto.wallet.domain.WalletManagedPosition",
            "com.crypto.wallet.domain.WalletSettings","com.crypto.wallet.domain.WalletTrade",
            "com.crypto.wallet.domain.WalletDailyStatistics"));factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-only","hibernate.show_sql","false","hibernate.jdbc.time_zone","UTC"));factory.afterPropertiesSet();
        // Hibernate-generated schema needs the database-managed defaults from V28.
        jdbc.execute("ALTER TABLE wallet_asset MODIFY created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), MODIFY updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)");
        // Match the production lookup index; Hibernate's entity-only DDL omits
        // this migration index and otherwise turns the lock probe into a table scan.
        jdbc.execute("CREATE INDEX idx_wallet_managed_symbol_status_opened ON wallet_managed_position(symbol,status,opened_at)");
        manager=new JpaTransactionManager(factory.getObject());manager.setDataSource(pool);tx=new TransactionTemplate(manager);
        em=SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        repos=new JpaRepositoryFactory(em);
        repos.addRepositoryProxyPostProcessor((proxy,info)->{
            String simple=info.getRepositoryInterface().getSimpleName();
            String bean=Character.toLowerCase(simple.charAt(0))+simple.substring(1);
            proxy.addAdvice((org.aopalliance.intercept.MethodInterceptor) call->{
                WalletMutationRepositoryGuard.check(bean,call.getMethod().getName());return call.proceed();
            });
        });assets=repos.getRepository(WalletAssetRepository.class);positions=repos.getRepository(WalletManagedPositionRepository.class);trades=repos.getRepository(WalletTradeRepository.class);
        var settings=repos.getRepository(WalletSettingsRepository.class);var daily=repos.getRepository(WalletDailyStatisticsRepository.class);
        var portfolio=mock(WalletService.class);when(portfolio.currentPortfolioValue()).thenReturn(new BigDecimal("1000"));
        wallet=new WalletAutoExecutionService(assets,trades,positions,settings,new WalletExecutionSizingPolicy(),mock(BinanceMinimumExecutionPolicy.class),daily,portfolio,new ObjectMapper().findAndRegisterModules());
        jdbc.execute("CREATE TABLE coin_configuration(symbol VARCHAR(30))");
        String coordinationDdl=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/db/migration/V92__fix_125_wallet_coordination.sql")).replaceAll("(?m)^\\s*--.*$", "");
        for(String sql:coordinationDdl.split(";"))if(!sql.isBlank())jdbc.execute(sql);
        guard=new WalletTransactionCoordination(jdbc,manager);guard.provision("BTCUSDT");guard.provision("ETHUSDT");
        ReflectionTestUtils.setField(wallet,"walletCoordination",guard);ReflectionTestUtils.setField(wallet,"walletEntityManager",em);
        var proxy=new org.springframework.aop.framework.ProxyFactory(wallet);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        wallet=(WalletAutoExecutionService)proxy.getProxy();
        tx.executeWithoutResult(t->{
            guard.mutation();
            assets.save(WalletAsset.builder().symbol("USDT").quantity(new BigDecimal("1000")).averageBuyPriceUsdt(BigDecimal.ONE).enabled(true).build());
        });
    }
    @Test void repositoryMutationCannotBypassGuardOrInheritItAcrossRequiresNew() {
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(t->assets.creditQuantity("USDT",BigDecimal.ONE)));
        tx.executeWithoutResult(t->{
            guard.mutation();
            assertDoesNotThrow(()->WalletTransactionCoordination.requireMutation());
            var inner=new TransactionTemplate(manager);inner.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            assertThrows(IllegalStateException.class,()->inner.executeWithoutResult(i->assets.creditQuantity("USDT",BigDecimal.ONE)));
            assertDoesNotThrow(()->assets.creditQuantity("USDT",BigDecimal.ONE));
        });
        assertFalse(WalletTransactionCoordination.mutationHeld());
        assertMoney("1001",jdbc.queryForObject("SELECT quantity FROM wallet_asset WHERE symbol='USDT'",BigDecimal.class));
    }
    @Test void slowSnapshotHoldsGlobalGuardAndBlocksUnrelatedSymbolUntilCommit() throws Exception {
        // Existing positions isolate MUTATION contention from InnoDB empty-range
        // locks on the first-position lookup (covered separately by flat tests).
        assertTrue(buy(signal("BTCUSDT")));assertTrue(buy(signal("ETHUSDT")));
        var meter=new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        ReflectionTestUtils.setField(guard,"meters",meter);
        WalletAutoExecutionService target=org.springframework.test.util.AopTestUtils.getTargetObject(wallet);
        var portfolio=(WalletService)ReflectionTestUtils.getField(target,"walletService");
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean first=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(i->{if(first.getAndSet(false)){entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));}return null;}).when(portfolio).captureSnapshot();
        var a=signal("BTCUSDT");var b=signal("ETHUSDT");var threads=Executors.newFixedThreadPool(2);
        try {
            Future<?> f=threads.submit(()->assertTrue(buy(a)));assertTrue(entered.await(5,TimeUnit.SECONDS));
            Future<?> g=threads.submit(()->assertTrue(buy(b)));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
            while(System.nanoTime()<deadline){
                int locks=admin.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA=? AND l.OBJECT_NAME='wallet_mutation_coordination'",Integer.class,schema);
                if(locks>0){waiting=true;break;}Thread.onSpinWait();
            }
            if(!waiting)System.out.println("[FIX-125][LOCK_PROBE] "+admin.queryForList("SELECT l.OBJECT_NAME,l.INDEX_NAME,l.LOCK_TYPE,l.LOCK_MODE,l.LOCK_DATA FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA=?",schema));
            assertTrue(waiting,"ETH must block specifically on wallet-wide mutation guard during BTC snapshot");
            assertFalse(g.isDone());release.countDown();f.get(10,TimeUnit.SECONDS);g.get(10,TimeUnit.SECONDS);
            var hold=meter.get("wallet.coordination.hold").tag("stage","MUTATION").timer();
            var wait=meter.get("wallet.coordination.wait").tag("stage","MUTATION").timer();
            assertEquals(2,hold.count());assertEquals(2,wait.count());
            System.out.printf("[FIX-125][CONTROLLED_CONTENTION_MEASUREMENT] mutations=%d maxWaitMs=%.3f maxHoldMs=%.3f; latch-controlled snapshot, not Production throughput%n",hold.count(),wait.max(TimeUnit.MILLISECONDS),hold.max(TimeUnit.MILLISECONDS));
        } finally {release.countDown();threads.shutdown();assertTrue(threads.awaitTermination(10,TimeUnit.SECONDS));meter.close();}
    }
    TradeSignal signal(String symbol) {
        return tx.execute(t->{var s=new TradeSignal();s.setSymbol(symbol);s.setInterval("1m");s.setCandleOpenTime(Instant.now());s.setGeneratedAt(Instant.now());s.setLatestPrice(new BigDecimal("10"));s.setStopLoss(new BigDecimal("9"));s.setTakeProfit(new BigDecimal("12"));s.setDecision(com.crypto.domain.SignalDecision.BUY);s.setConfidenceScore(80);s.setTotalScore(80);// Populate required descriptive fields; no decision policy is mocked by this fixture.
            for(var f:TradeSignal.class.getDeclaredFields()) {
                var c=f.getAnnotation(jakarta.persistence.Column.class);
                if(c!=null && !c.nullable() && (f.getType()==String.class || f.getType().isEnum())) {
                    try{f.setAccessible(true);if(f.get(s)==null)f.set(s,f.getType().isEnum()?f.getType().getEnumConstants()[0]:"TEST");}catch(IllegalAccessException ex){throw new IllegalStateException(ex);}
                }
            }
            em.persist(s);return s;});
    }
    boolean buy(TradeSignal s) {return wallet.executeBuy(s,new BigDecimal("10"),Instant.now(),50,"test","ENTRY_BUY",80);}
    void contend(TradeSignal a, TradeSignal b, boolean rollback) throws Exception {
        var done=new CountDownLatch(1);var release=new CountDownLatch(1);var threads=Executors.newFixedThreadPool(2);
        try {
            Future<?> first=threads.submit(()->tx.executeWithoutResult(t->{assertTrue(buy(a));done.countDown();try{assertTrue(release.await(15,TimeUnit.SECONDS));}catch(InterruptedException ex){throw new RuntimeException(ex);}if(rollback)t.setRollbackOnly();}));
            if(!done.await(5,TimeUnit.SECONDS)){first.get(1,TimeUnit.SECONDS);fail("first buy did not reach commit barrier");}
            Future<?> second=threads.submit(()->tx.executeWithoutResult(t->{assertTrue(buy(b));}));
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
            while(System.nanoTime()<until){if(admin.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits",Integer.class)>0){waiting=true;break;}Thread.onSpinWait();}
            assertTrue(waiting,"Second transaction must genuinely wait on InnoDB, not a mock/monitor");
            assertFalse(second.isDone());release.countDown();first.get(15,TimeUnit.SECONDS);second.get(15,TimeUnit.SECONDS);
        } finally {release.countDown();threads.shutdownNow();}
    }
    @Test void flatSymbolWaitsThroughCommitAndRetainsBothAllowedAdds() throws Exception {
        contend(signal("BTCUSDT"),signal("BTCUSDT"),false);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM wallet_managed_position WHERE status='OPEN'",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM wallet_trade WHERE side='BUY'",Integer.class));
        assertMoney("900",jdbc.queryForObject("SELECT quantity FROM wallet_asset WHERE symbol='USDT'",BigDecimal.class));
        assertMoney("10",jdbc.queryForObject("SELECT quantity FROM wallet_asset WHERE symbol='BTC'",BigDecimal.class));
        assertMoney("100",jdbc.queryForObject("SELECT total_cost_usdt FROM wallet_managed_position",BigDecimal.class));
    }
    @Test void rollbackReleasesGuardAndDoesNotLeaveDebitOrTrade() throws Exception {
        contend(signal("BTCUSDT"),signal("BTCUSDT"),true);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM wallet_trade WHERE side='BUY'",Integer.class));
        assertMoney("950",jdbc.queryForObject("SELECT quantity FROM wallet_asset WHERE symbol='USDT'",BigDecimal.class));
        assertMoney("50",jdbc.queryForObject("SELECT total_cost_usdt FROM wallet_managed_position",BigDecimal.class));
    }
    @Test void differentSymbolsShareCashWithoutLosingDebits() throws Exception {
        contend(signal("BTCUSDT"),signal("ETHUSDT"),false);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM wallet_managed_position",Integer.class));
        assertMoney("900",jdbc.queryForObject("SELECT quantity FROM wallet_asset WHERE symbol='USDT'",BigDecimal.class));
    }
    @Test void existingPositionTwoExitCallersCannotSellTwice() throws Exception {
        var entry=signal("BTCUSDT");tx.executeWithoutResult(t->assertTrue(buy(entry)));
        var exit1=signal("BTCUSDT");var exit2=signal("BTCUSDT");
        race(()->tx.executeWithoutResult(t->wallet.executeSignalLinkedExit(exit1,"STOP_LOSS","test")),
             ()->tx.executeWithoutResult(t->wallet.executeSignalLinkedExit(exit2,"STOP_LOSS","test")));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM wallet_trade WHERE side='SELL'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM wallet_managed_position WHERE status='OPEN'",Integer.class));
        assertMoney("1000",jdbc.queryForObject("SELECT quantity FROM wallet_asset WHERE symbol='USDT'",BigDecimal.class));
        assertMoney("0",jdbc.queryForObject("SELECT quantity FROM wallet_asset WHERE symbol='BTC'",BigDecimal.class));
    }
    @Test void liveProtectionAndSignalExitShareCommitCoordination() throws Exception {
        var entry=signal("BTCUSDT");tx.executeWithoutResult(t->assertTrue(buy(entry)));
        var service=new com.crypto.position.service.LivePositionProtectionService(positions,
            repos.getRepository(com.crypto.repository.PaperPositionRepository.class),mock(com.crypto.position.service.DynamicProfitLockService.class),
            mock(com.crypto.position.service.PositionContinuationPolicy.class),new com.crypto.position.service.PositionExitPolicy(),
            repos.getRepository(com.crypto.repository.TradeSignalRepository.class),wallet,mock(com.crypto.audit.service.ProductionExitAuditService.class),
            mock(com.crypto.position.repository.PositionManagementEventRepository.class),mock(com.crypto.position.service.NearTpFailureProtectionPolicy.class));
        ReflectionTestUtils.setField(service,"walletCoordination",guard);
        var exit=signal("BTCUSDT");
        race(()->tx.executeWithoutResult(t->service.onPrice("BTCUSDT",new BigDecimal("9"))),
            ()->tx.executeWithoutResult(t->wallet.executeSignalLinkedExit(exit,"STOP_LOSS","test")));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM wallet_trade WHERE side='SELL'",Integer.class));
        assertMoney("995",jdbc.queryForObject("SELECT quantity FROM wallet_asset WHERE symbol='USDT'",BigDecimal.class));
        assertMoney("0",jdbc.queryForObject("SELECT quantity FROM wallet_asset WHERE symbol='BTC'",BigDecimal.class));
    }
    @Test void actualSignalCoordinatorAndPaperBodyPreserveBothAdds() throws Exception {
        String ddl=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/db/migration/V86__fix_127_signal_processing.sql")).replaceAll("(?m)^\\s*--.*$", "");
        for(String sql:ddl.split(";"))if(!sql.isBlank())jdbc.execute(sql);
        jdbc.execute("ALTER TABLE signal_processing_work ADD source_event_id BIGINT NULL");
        var store=new com.crypto.execution.processing.SignalProcessingStore(jdbc,manager);
        var signalRepo=repos.getRepository(com.crypto.repository.TradeSignalRepository.class);
        var coordinator=new com.crypto.execution.processing.SignalProcessingCoordinator(store,signalRepo,positions,new com.crypto.execution.processing.SignalProcessingFreshness(jdbc),manager);
        ReflectionTestUtils.setField(coordinator,"walletCoordination",guard);
        var intelligence=mock(com.crypto.execution.service.ExecutionIntelligenceService.class);
        var decision=com.crypto.execution.service.ExecutionIntelligenceService.ExecutionDecision.allow("TEST","TEST",50,"test",mock(com.crypto.execution.service.ExecutionIntelligenceService.Evidence.class));
        when(intelligence.evaluateSetupTimeframeWakeup(any(),anyInt())).thenReturn(com.crypto.execution.service.ExecutionIntelligenceService.SetupWakeupEvaluation.none());
        when(intelligence.evaluateConfirmedSetupWakeup(any(),anyInt())).thenReturn(com.crypto.execution.service.ExecutionIntelligenceService.SetupWakeupEvaluation.none());
        when(intelligence.evaluateBuy(any())).thenReturn(decision);when(intelligence.evaluateBuy(any(),anyInt(),any())).thenReturn(decision);
        when(intelligence.revalidateAtExecutionPrice(any(),any(),any())).thenReturn(decision);
        when(intelligence.assessEntryQualityAtPrice(any(),any())).thenReturn(new com.crypto.execution.service.ExecutionIntelligenceService.EntryQuality(80,"TEST",0,0,2,1));
        var prices=mock(com.crypto.execution.service.ExecutionPriceAuthorityService.class);
        when(prices.resolve(any(),any())).thenAnswer(i->Optional.of(new com.crypto.execution.service.ExecutionPriceAuthorityService.ExecutionPrice(new BigDecimal("10"),Instant.now(),"TEST")));
        var dynamic=mock(com.crypto.position.service.DynamicProfitLockService.class);
        when(dynamic.evaluate(any())).thenReturn(com.crypto.position.service.DynamicProfitLockService.Evaluation.inactive("test"));
        var properties=new com.crypto.config.TradingProperties(List.of("BTCUSDT"),List.of("1m"),300,50,BigDecimal.ONE,BigDecimal.TEN,100,new BigDecimal("1000"),false,60000);
        var paper=new com.crypto.service.PaperTradingService(properties,signalRepo,repos.getRepository(com.crypto.repository.PaperPositionRepository.class),wallet,
            mock(com.crypto.audit.service.ProductionExitAuditService.class),trades,positions,mock(com.crypto.service.TradeExecutionValidationService.class),intelligence,prices,dynamic,new com.crypto.position.service.PositionPriceAuthorityPolicy());
        ReflectionTestUtils.setField(paper,"processingCoordinator",coordinator);
        var a=signal("BTCUSDT");var b=signal("BTCUSDT");
        race(()->assertTrue(paper.processSignal(a).isPresent()),()->assertTrue(paper.processSignal(b).isPresent()));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM signal_processing_work WHERE status='COMPLETED'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM paper_position WHERE status='OPEN'",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM wallet_trade WHERE side='BUY'",Integer.class));
        assertMoney("100",jdbc.queryForObject("SELECT total_cost_usdt FROM wallet_managed_position",BigDecimal.class));
        assertMoney("900",jdbc.queryForObject("SELECT quantity FROM wallet_asset WHERE symbol='USDT'",BigDecimal.class));
    }
    @Test void manualBuyAndAutomaticBuyRetainBothAssetUpdates() throws Exception {
        var manual=new WalletService(assets,trades,mock(WalletCashFlowRepository.class),mock(WalletSnapshotRepository.class),
            repos.getRepository(com.crypto.repository.TradeSignalRepository.class),mock(com.crypto.repository.CandleRepository.class),
            repos.getRepository(WalletSettingsRepository.class),repos.getRepository(WalletDailyStatisticsRepository.class));
        ReflectionTestUtils.setField(manual,"walletCoordination",guard);ReflectionTestUtils.setField(manual,"walletEntityManager",em);
        var entry=signal("BTCUSDT");
        race(()->tx.executeWithoutResult(t->assertTrue(buy(entry))),()->tx.executeWithoutResult(t->manual.execute(
            new com.crypto.wallet.dto.WalletTradeRequest(null,"BTCUSDT","BUY",new BigDecimal("3"),new BigDecimal("10"),BigDecimal.ZERO,"MANUAL","test"))));
        assertMoney("8",jdbc.queryForObject("SELECT quantity FROM wallet_asset WHERE symbol='BTC'",BigDecimal.class));
        assertMoney("10",jdbc.queryForObject("SELECT average_buy_price_usdt FROM wallet_asset WHERE symbol='BTC'",BigDecimal.class));
        assertMoney("920",jdbc.queryForObject("SELECT quantity FROM wallet_asset WHERE symbol='USDT'",BigDecimal.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM wallet_trade",Integer.class));
    }
    @Test void duplicateBuyUsesCurrentLedgerAfterWaitingForCommit() throws Exception {
        var entry=signal("BTCUSDT");
        race(()->tx.executeWithoutResult(t->assertTrue(buy(entry))),()->tx.executeWithoutResult(t->assertTrue(buy(entry))));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM wallet_trade",Integer.class));
        assertMoney("950",jdbc.queryForObject("SELECT quantity FROM wallet_asset WHERE symbol='USDT'",BigDecimal.class));
    }
    @Test void cancelledGuardWaitRollsBackAndReturnsPooledConnections() throws Exception {
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var workers=Executors.newFixedThreadPool(2);
        try {
            var first=workers.submit(()->tx.executeWithoutResult(t->{guard.symbol("BTCUSDT");locked.countDown();try{assertTrue(release.await(8,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}}));
            assertTrue(locked.await(3,TimeUnit.SECONDS));
            var second=workers.submit(()->tx.executeWithoutResult(t->{guard.symbol("BTCUSDT");fail("contender cannot acquire held lock");}));
            var failure=assertThrows(ExecutionException.class,()->second.get(5,TimeUnit.SECONDS));
            assertInstanceOf(org.springframework.dao.DataAccessException.class,failure.getCause());
            release.countDown();first.get(3,TimeUnit.SECONDS);
            tx.executeWithoutResult(t->guard.symbol("BTCUSDT"));
            assertEquals(0,pool.getHikariPoolMXBean().getActiveConnections());
            assertEquals(1,jdbc.queryForObject("SELECT 1",Integer.class));
        }finally{release.countDown();workers.shutdownNow();}
    }
    // Latch is installed by the first mutation, but blocks in BEFORE COMMIT after
    // the entire real caller/body has returned. The contender must be in InnoDB.
    void race(Runnable firstWork,Runnable secondWork) throws Exception {
        var atCommit=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new java.util.concurrent.atomic.AtomicBoolean();
        var actualGuard=guard;var guarded=spy(guard);
        doAnswer(invocation->{invocation.callRealMethod();if(once.compareAndSet(false,true))org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
            @Override public void beforeCommit(boolean readOnly){atCommit.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException ex){throw new RuntimeException(ex);}}
        });return null;}).when(guarded).mutation();
        var target=((org.springframework.aop.framework.Advised)wallet).getTargetSource().getTarget();
        ReflectionTestUtils.setField(target,"walletCoordination",guarded);
        var workers=Executors.newFixedThreadPool(2);
        try {
            Future<?> first=workers.submit(firstWork);if(!atCommit.await(5,TimeUnit.SECONDS)){first.get(1,TimeUnit.SECONDS);fail("commit barrier not reached");}
            Future<?> second=workers.submit(secondWork);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);boolean blocked=false;
            while(System.nanoTime()<deadline){if(admin.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits",Integer.class)>0){blocked=true;break;}Thread.yield();}
            assertTrue(blocked);assertFalse(second.isDone());release.countDown();first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
        }finally{release.countDown();workers.shutdownNow();ReflectionTestUtils.setField(target,"walletCoordination",actualGuard);}
    }
    static void assertMoney(String expected,BigDecimal actual){assertEquals(0,new BigDecimal(expected).compareTo(actual));}
    @AfterEach void cleanup(){if(factory!=null)factory.destroy();if(pool!=null)pool.close();if(admin!=null&&schema!=null)admin.execute("DROP DATABASE IF EXISTS "+schema);}
}
