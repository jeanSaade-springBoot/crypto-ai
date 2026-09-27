package com.crypto.shared;

import com.crypto.repository.CandleRepository;
import com.crypto.market.service.MarketPriceEventService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.data.domain.PageRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SharedCandleReadTest {
    JdbcTemplate local,remote;SharedMarketSource source;CandleRepository repo;
    Instant t=Instant.parse("2026-09-01T12:00:00Z");
    JdbcTemplate database() { return new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","")); }
    @BeforeEach void setup() {
        local=database();remote=database();source=mock(SharedMarketSource.class);when(source.reader()).thenReturn(remote);repo=new CandleRepository(source,local);
        remote.execute("""
            CREATE TABLE candle(id BIGINT PRIMARY KEY,symbol VARCHAR(30),interval_code VARCHAR(10),open_time TIMESTAMP,close_time TIMESTAMP,
            open_price DECIMAL(30,12),high_price DECIMAL(30,12),low_price DECIMAL(30,12),close_price DECIMAL(30,12),volume DECIMAL(38,12),
            quote_asset_volume DECIMAL(38,12),number_of_trades BIGINT,taker_buy_base_volume DECIMAL(38,12),taker_buy_quote_volume DECIMAL(38,12),closed BOOLEAN)
            """);
        for(int n=0;n<3;n++) remote.update("INSERT INTO candle VALUES(?,'BTCUSDT','1m',?,?,1,2,1,?,10,NULL,NULL,NULL,NULL,?)",100+n,Timestamp.from(t.plusSeconds(n*60)),Timestamp.from(t.plusSeconds(n*60+59)),n+1,n<2);
        for(String table:List.of("technical_indicator","trade_signal"))local.execute("CREATE TABLE "+table+"(symbol VARCHAR(30),interval_code VARCHAR(10),candle_open_time TIMESTAMP)");
    }
    @Test void exactHistoricalPredicatesUseSharedSourceEvenWithoutLocalCandleTable() {
        assertEquals(100L,repo.findBySymbolAndIntervalCodeAndOpenTime("BTCUSDT","1m",t).orElseThrow().getId());
        assertTrue(repo.findBySymbolAndIntervalCodeAndOpenTime("BTCUSDT","1m",t.plusSeconds(1)).isEmpty());
        assertEquals(1,repo.findClosedCandlesAtOrBefore("BTCUSDT","1m",t,PageRequest.of(0,100)).size());
        assertTrue(repo.findClosedCandlesClosedAtOrBefore("BTCUSDT","1m",t,PageRequest.of(0,100)).isEmpty());
        assertEquals(2,repo.findBySymbolAndIntervalCodeAndClosedTrueAndOpenTimeBetweenOrderByOpenTimeAsc("BTCUSDT","1m",t,t.plusSeconds(180)).size());
        assertEquals(3,repo.findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeAsc("BTCUSDT","1m",t,t.plusSeconds(180)).size());
        assertEquals(2,repo.countBySymbolAndIntervalCodeAndClosedTrue("BTCUSDT","1m"));
    }
    @Test void recoveryJoinsLocalEvidenceByLineageNotForeignCandleIds() {
        local.update("INSERT INTO technical_indicator VALUES('BTCUSDT','1m',?)",Timestamp.from(t));
        local.update("INSERT INTO trade_signal VALUES('BTCUSDT','1m',?)",Timestamp.from(t));
        var missing=repo.findClosedCandlesMissingAnalysisThrough("BTCUSDT","1m",t,t.plusSeconds(180),PageRequest.of(0,1));
        assertEquals(1,missing.size());assertEquals(t.plusSeconds(60),missing.getFirst().getOpenTime());
    }
    @Test void sourceWriteGuardRejectsImportBeforeAnyLocalWrite() {
        doThrow(new IllegalStateException("shared source")).when(source).requireLocalWriter();
        assertThrows(IllegalStateException.class,()->repo.upsert("BTCUSDT","1m",t,t,null,null,null,null,null,null,null,null,null,true));
    }
    @Test void replayPriceHistoryKeepsProvenanceAndExcludesSkippedObservations() {
        local.execute("CREATE TABLE market_price_event(id BIGINT PRIMARY KEY,symbol VARCHAR(30),observed_at TIMESTAMP,price DECIMAL,source VARCHAR(40),delivery_status VARCHAR(40))");
        local.update("INSERT INTO market_price_event VALUES(1,'BTCUSDT',?,1,'BINANCE_KLINE_LIVE_CLOSE','LEGACY'),(2,'BTCUSDT',?,2,'COLLECTOR_KLINE_LIVE','APPLIED'),(3,'BTCUSDT',?,3,'COLLECTOR_KLINE_LIVE','HISTORICAL_ONLY')",Timestamp.from(t),Timestamp.from(t),Timestamp.from(t));
        var history=new MarketPriceEventService(local).find("BTCUSDT",t,t);
        assertEquals(2,history.size());assertEquals("COLLECTOR_KLINE_LIVE",history.get(1).source());
        assertEquals(0,history.get(0).price().compareTo(java.math.BigDecimal.ONE));
        assertEquals(0,new MarketPriceEventService(local).findLatestAtOrBefore("BTCUSDT",t).orElseThrow().price().compareTo(java.math.BigDecimal.valueOf(2)));
    }
    @Test void correctedInputChangesFingerprintWithoutChangingIdentity() {
        local.execute("CREATE TABLE shared_candle_input_audit(id BIGINT AUTO_INCREMENT PRIMARY KEY,context_key VARCHAR(100),read_index INT,input_hash VARCHAR(64),row_count INT,query_text VARCHAR(1000))");
        try(var audit=CandleInputAudit.open(local,"REPLAY:1")) {
            repo.findClosedCandles("BTCUSDT","1m",PageRequest.of(0,10));
            remote.update("UPDATE candle SET close_price=99 WHERE id=100");
            repo.findClosedCandles("BTCUSDT","1m",PageRequest.of(0,10));
        }
        var hashes=local.queryForList("SELECT input_hash FROM shared_candle_input_audit ORDER BY id",String.class);
        assertNotEquals(hashes.get(0),hashes.get(1));
        assertEquals(t,repo.findBySymbolAndIntervalCodeAndOpenTime("BTCUSDT","1m",t).orElseThrow().getOpenTime());
    }

    @Test void knownSkippedCollectorWindowCannotSilentlyBecomeSignalPriceFallback() {
        local.execute("CREATE TABLE market_price_event(symbol VARCHAR(30),observed_at TIMESTAMP,source_event_id BIGINT,delivery_status VARCHAR(40))");
        local.execute("CREATE TABLE shared_market_cutover_history(symbol VARCHAR(30),cutover_at TIMESTAMP)");
        var prices=new MarketPriceEventService(local);
        prices.assertReplayEvidence("BTCUSDT",t,t.plusSeconds(60)); // genuine legacy empty window
        local.update("INSERT INTO shared_market_cutover_history VALUES('BTCUSDT',?)",Timestamp.from(t));
        local.update("INSERT INTO market_price_event VALUES('BTCUSDT',?,1,'HISTORICAL_ONLY')",Timestamp.from(t));
        assertThrows(IllegalStateException.class,()->prices.assertReplayEvidence("BTCUSDT",t,t.plusSeconds(60)));
        local.update("UPDATE market_price_event SET delivery_status='APPLIED'");
        prices.assertReplayEvidence("BTCUSDT",t,t.plusSeconds(60));
        local.update("UPDATE market_price_event SET delivery_status='PENDING'");
        assertThrows(IllegalStateException.class,()->prices.assertReplayEvidence("BTCUSDT",t,t.plusSeconds(60)));
    }
}
