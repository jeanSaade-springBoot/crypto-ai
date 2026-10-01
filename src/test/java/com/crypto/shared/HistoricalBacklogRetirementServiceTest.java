package com.crypto.shared;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import com.crypto.administration.service.CoinConfigurationService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** FIX-140: real H2 transaction tests for opt-in retirement; MySQL acceptance remains required. */
class HistoricalBacklogRetirementServiceTest {
    JdbcTemplate jdbc;
    HistoricalBacklogRetirementService service;
    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE shared_market_consumer_state(symbol VARCHAR(30) PRIMARY KEY)");
        jdbc.execute("INSERT INTO shared_market_consumer_state VALUES('BTCUSDT')");
        jdbc.execute("CREATE TABLE shared_market_event_delivery(source_event_id BIGINT PRIMARY KEY,symbol VARCHAR(30),interval_code VARCHAR(10),phase INT,status VARCHAR(40),analysis_status VARCHAR(40),symbol_sequence BIGINT,candle_open_time TIMESTAMP(6),candle_close_time TIMESTAMP(6),analysis_started_at TIMESTAMP(6),analysis_completed_at TIMESTAMP(6),owner_token VARCHAR(36),protection_started_at TIMESTAMP(6),protection_completed_at TIMESTAMP(6),analysis_retired_at TIMESTAMP(6),analysis_retirement_operation VARCHAR(36),analysis_retirement_reason VARCHAR(255))");
        jdbc.execute("CREATE TABLE signal_processing_work(signal_id BIGINT,source_event_id BIGINT,symbol VARCHAR(30),interval_code VARCHAR(10),candle_open_time TIMESTAMP(6))");
        jdbc.execute("CREATE TABLE trade_signal(id BIGINT,symbol VARCHAR(30),interval_code VARCHAR(10),candle_open_time TIMESTAMP(6))");
        jdbc.execute("CREATE TABLE wallet_trade(id BIGINT,signal_id BIGINT)");
        var coins=mock(CoinConfigurationService.class);when(coins.enabledSymbols()).thenReturn(List.of("BTCUSDT"));
        var source=mock(SharedMarketSource.class);when(source.enabled()).thenReturn(true);
        service=new HistoricalBacklogRetirementService(jdbc,new DataSourceTransactionManager(ds),coins,source);
        ReflectionTestUtils.setField(service,"historyEnabled",false);
        ReflectionTestUtils.setField(service,"retirementEnabled",true);
        ReflectionTestUtils.setField(service,"dryRun",false);
        ReflectionTestUtils.setField(service,"retainSeconds",0L);
        jdbc.update("INSERT INTO shared_market_event_delivery(source_event_id,symbol,interval_code,phase,status,analysis_status,symbol_sequence,candle_open_time,candle_close_time,owner_token) VALUES(1,'BTCUSDT','1m',4,'COMPLETED','PENDING',1,?,?,'price-phase-owner')",Timestamp.from(Instant.now().minusSeconds(3660)),Timestamp.from(Instant.now().minusSeconds(3600)));
    }
    String status() {return jdbc.queryForObject("SELECT analysis_status FROM shared_market_event_delivery",String.class);}
    @Test void untouchedExpiredAnalysisIsRetiredWithoutFakeCompletion() {
        service.retire();assertEquals("HISTORICAL_SKIPPED",status());
        assertNull(jdbc.queryForObject("SELECT analysis_completed_at FROM shared_market_event_delivery",Timestamp.class));
        assertNotNull(jdbc.queryForObject("SELECT analysis_retired_at FROM shared_market_event_delivery",Timestamp.class));
        assertEquals("price-phase-owner",jdbc.queryForObject("SELECT owner_token FROM shared_market_event_delivery",String.class));
    }
    @Test void processingEvidencePreventsRetirement() {
        jdbc.update("INSERT INTO signal_processing_work(signal_id,source_event_id) VALUES(7,1)");
        service.retire();assertEquals("PENDING",status());
    }
    @Test void incompleteProtectionPreventsRetirement() {
        jdbc.update("UPDATE shared_market_event_delivery SET protection_started_at=CURRENT_TIMESTAMP(6)");
        service.retire();assertEquals("PENDING",status());
    }
    @Test void dryRunDoesNotMutateLedger() {
        ReflectionTestUtils.setField(service,"dryRun",true);service.retire();assertEquals("PENDING",status());
    }
    @Test void historyEnabledPreventsRetirement() {
        ReflectionTestUtils.setField(service,"historyEnabled",true);service.retire();assertEquals("PENDING",status());
    }
    @Test void freshCandleSurvivesZeroExtraRetention() {
        jdbc.update("UPDATE shared_market_event_delivery SET candle_close_time=?",Timestamp.from(Instant.now().minusSeconds(5)));
        service.retire();assertEquals("PENDING",status());
    }
}
