package com.crypto.local;

import com.crypto.shared.*;
import com.crypto.indicator.event.CandleClosedAnalysisWorker;
import com.crypto.administration.service.CoinConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual OFF scheduling entry points against a database with no shared tables. */
class OffSharedRuntimeTest {
    @Test void noSharedPoolOrLedgerQueriesFromOffSchedulers(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID(),"sa","");var jdbc=new JdbcTemplate(ds);
        var source=new SharedMarketSource(jdbc,new MockEnvironment());assertSame(jdbc,source.reader());assertTrue(source.poolPressure().isEmpty());
        var coins=mock(CoinConfigurationService.class);var protect=mock(com.crypto.position.service.LivePositionProtectionService.class);
        var observer=mock(com.crypto.debug.monitor.service.PriceMoveMonitorService.class);var worker=mock(CandleClosedAnalysisWorker.class);
        var consumer=new SharedMarketConsumer(source,jdbc,coins,protect,observer,worker,Runnable::run,Runnable::run,new DataSourceTransactionManager(ds));
        try{consumer.start();consumer.validateCutover();consumer.discover();consumer.discoverFresh();consumer.dispatchPrices();consumer.dispatchProtectionTicks();consumer.dispatchAnalysis();consumer.dispatchHistoricalAnalysis();}
        finally{consumer.stop();}
        var dispatcher=new SharedAnalysisDispatcher(source,jdbc,coins,()->List.of("1m"),worker,Runnable::run,Runnable::run,new DataSourceTransactionManager(ds));
        dispatcher.acknowledgmentEnabled(true);dispatcher.ownerTimeoutReconciliationEnabled(true);
        dispatcher.live();dispatcher.historical();dispatcher.reconcileCompletedWorkAcknowledgments();dispatcher.reconcileAnalysisOnlyReviews();dispatcher.reconcileOwnerTimeoutReviews();dispatcher.monitor();dispatcher.stop();
        verifyNoInteractions(coins,protect,observer,worker);
        var authority=new SharedRecoveryAuthority(source,jdbc);assertFalse(authority.ownsLiveExecution());assertFalse(authority.deferCandle("BTCUSDT","1m",java.time.Instant.now(),java.time.Instant.now()));
    }
}
