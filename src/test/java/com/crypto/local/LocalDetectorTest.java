package com.crypto.local;

import com.crypto.indicator.event.*;
import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.client.config.binance.BinanceMarketDataProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** FIX-153 durable close-to-dispatch gap repair is bounded and never manufactures backfill signals. */
class LocalDetectorTest {
    final JdbcTemplate jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
    final CoinConfigurationService coins=mock(CoinConfigurationService.class);
    final BinanceMarketDataProperties props=new BinanceMarketDataProperties();
    final CandleClosedAnalysisWorker worker=mock(CandleClosedAnalysisWorker.class);
    final List<Runnable> tasks=new ArrayList<>();
    final CandleAnalysisDispatcher dispatcher=new CandleAnalysisDispatcher(tasks::add,worker);
    final Instant open=Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    LocalDetectorTest(){
        jdbc.execute("CREATE TABLE candle(symbol VARCHAR(32),interval_code VARCHAR(8),open_time TIMESTAMP(6),close_time TIMESTAMP(6),closed BOOLEAN,ingestion_source VARCHAR(16),close_transition_token VARCHAR(36))");
        for(String table:List.of("trade_signal","signal_processing_work"))jdbc.execute("CREATE TABLE "+table+"(symbol VARCHAR(32),interval_code VARCHAR(8),candle_open_time TIMESTAMP(6))");
        when(coins.enabledSymbols()).thenReturn(List.of("BTCUSDT"));props.setIntervals(List.of("1m"));
    }
    void candle(String provenance,Instant at){candle(provenance,at,"RUNTIME".equals(provenance)?UUID.randomUUID().toString():null);}
    void candle(String provenance,Instant at,String token){jdbc.update("INSERT INTO candle VALUES('BTCUSDT','1m',?,?,TRUE,?,?)",Timestamp.from(at),Timestamp.from(at.plusSeconds(59)),provenance,token);}
    LocalClosedCandleDetector detector(){var d=new LocalClosedCandleDetector(jdbc,coins,dispatcher,new CandleAnalysisExecutionCoordinator(),props);ReflectionTestUtils.setField(d,"mode","OFF");return d;}
    @Test void backfillAndLegacyNeverProduceTasksEvenAfterRestart(){
        candle("BACKFILL",open);candle(null,open.minusSeconds(60));detector().detect();detector().detect();
        assertTrue(tasks.isEmpty());verifyNoInteractions(worker);
    }
    @Test void legacyNullProvenanceClosedByRuntimeTokenIsRecovered(){
        candle(null,open,UUID.randomUUID().toString());detector().detect();assertEquals(1,tasks.size());
    }
    @Test void runtimeGapIsAdmittedOnceAndSignalOrWorkEvidencePreventsReadmission(){
        candle("RUNTIME",open);var d=detector();d.detect();d.detect();assertEquals(1,tasks.size());
        tasks.removeFirst().run();jdbc.update("INSERT INTO signal_processing_work VALUES('BTCUSDT','1m',?)",Timestamp.from(open));
        d.detect();assertTrue(tasks.isEmpty());verify(worker).processShared(any(),any(),eq(true));
        jdbc.update("DELETE FROM signal_processing_work");jdbc.update("INSERT INTO trade_signal VALUES('BTCUSDT','1m',?)",Timestamp.from(open));
        detector().detect();assertTrue(tasks.isEmpty());
    }
    @Test void runtimeOutsideLookbackIsNotAnalysed(){
        candle("RUNTIME",open.minusSeconds(600));detector().detect();assertTrue(tasks.isEmpty());
    }
    @Test void higherTimeframeLookbackIncludesItsWholeCandleDuration(){
        props.setIntervals(List.of("1h"));Instant hourOpen=Instant.now().minusSeconds(3601).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        jdbc.update("INSERT INTO candle VALUES('BTCUSDT','1h',?,?,TRUE,'RUNTIME',?)",Timestamp.from(hourOpen),Timestamp.from(hourOpen.plusSeconds(3599)),UUID.randomUUID().toString());
        detector().detect();assertEquals(1,tasks.size());tasks.removeFirst().run();
        verify(worker).processShared(any(),any(),eq(true));
    }
    @Test void detectorDoesNotWaitBehindAnExistingCandleLock()throws Exception{
        candle("RUNTIME",open);var coordination=new CandleAnalysisExecutionCoordinator();
        var d=new LocalClosedCandleDetector(jdbc,coins,dispatcher,coordination,props);ReflectionTestUtils.setField(d,"mode","OFF");
        try(var pool=java.util.concurrent.Executors.newSingleThreadExecutor();var held=coordination.lock("BTCUSDT","1m",open)){
            pool.submit(d::detect).get(1,java.util.concurrent.TimeUnit.SECONDS);assertTrue(tasks.isEmpty());
        }
        d.detect();assertEquals(1,tasks.size());
    }
    @Test void sharedModeDetectorIsInert(){
        candle("RUNTIME",open);var d=detector();ReflectionTestUtils.setField(d,"mode","LIVE");d.detect();assertTrue(tasks.isEmpty());verifyNoInteractions(coins);
    }
}
