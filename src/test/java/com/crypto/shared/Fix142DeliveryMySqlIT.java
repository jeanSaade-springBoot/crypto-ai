package com.crypto.shared;

import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.position.service.LivePositionProtectionService;
import com.crypto.debug.monitor.service.PriceMoveMonitorService;
import com.crypto.indicator.event.CandleClosedAnalysisWorker;
import static org.mockito.Mockito.*;

/** Runs the same delivery contracts against TWO fresh InnoDB schemas. Requires
 * a dedicated loopback MySQL test instance with CREATE/DROP and performance_schema
 * privileges. Only generated fix142_test_* schemas are created/dropped.
 * Business collaborators are mocked: this is delivery/ownership coverage, not full strategy parity. */
@Timeout(30)
class Fix142DeliveryMySqlIT extends Fix142DeliveryTest {
    private JdbcTemplate admin;
    private String localSchema, sourceSchema;
    @Override @BeforeEach void setup() throws Exception {
        String url=System.getenv("FIX142_TEST_MYSQL_URL");
        if(url==null || !url.matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/fix142_test_[A-Za-z0-9_]+(?:\\?.*)?"))
            throw new IllegalArgumentException("Use dedicated loopback MySQL and a fix142_test_ URL; missing configuration fails, never skips");
        if(!"YES".equals(System.getenv("FIX142_TEST_MYSQL_DISPOSABLE")))
            throw new IllegalArgumentException("Explicit dedicated disposable MySQL confirmation required");
        String user=System.getenv().getOrDefault("FIX142_TEST_MYSQL_USER","root");
        String password=System.getenv().getOrDefault("FIX142_TEST_MYSQL_PASSWORD","");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        String expected=System.getenv("FIX142_TEST_MYSQL_SERVER_UUID");
        String actual=admin.queryForObject("SELECT @@server_uuid",String.class);
        if(expected==null||!expected.equals(actual))throw new IllegalArgumentException("Dedicated test-server UUID does not match; no schemas created");
        String suffix=UUID.randomUUID().toString().replace("-","");
        localSchema="fix142_test_local_"+suffix;sourceSchema="fix142_test_source_"+suffix;
        admin.execute("CREATE DATABASE "+localSchema);admin.execute("CREATE DATABASE "+sourceSchema);
        String server=url.substring(0,url.indexOf('/',"jdbc:mysql://".length())+1);
        String options="?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
        var ds=new DriverManagerDataSource(server+localSchema+options,user,password);
        local=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);
        feed=new JdbcTemplate(new DriverManagerDataSource(server+sourceSchema+options,user,password));
        local.execute("CREATE TABLE market_price_event(id BIGINT AUTO_INCREMENT PRIMARY KEY,symbol VARCHAR(30),observed_at TIMESTAMP(6),price DECIMAL(30,12),source VARCHAR(40)) ENGINE=InnoDB");
        for(String migration:List.of("V89__fix_132_shared_market_delivery.sql","V90__fix_132_delivery_exclusion_reason.sql"))
            for(String sql:Files.readString(Path.of("src/main/resources/db/migration",migration)).replaceAll("(?m)^\\s*--.*$", "").split(";"))
                if(!sql.isBlank())local.execute(sql);
        local.execute("ALTER TABLE shared_market_event_delivery ADD COLUMN analysis_not_before TIMESTAMP(6)");
        local.execute("CREATE TABLE signal_processing_work(signal_id BIGINT PRIMARY KEY,status VARCHAR(30),symbol VARCHAR(30),interval_code VARCHAR(10),candle_open_time TIMESTAMP(6))");
        String approvalDdl=Files.readString(Path.of("src/main/resources/db/migration/V91__fix_132_explicit_cutover_ownership.sql")).replaceAll("(?m)^\\s*--.*$", "");
        for(String sql:approvalDdl.split(";"))if(!sql.isBlank())local.execute(sql);
        for(String sql:Files.readString(Path.of("src/main/resources/db/migration/V94__fix_132_reviewed_cutover_proposal.sql")).replaceAll("(?m)^\\s*--.*$", "").split(";"))if(!sql.isBlank())local.execute(sql);
        local.execute("CREATE TABLE effects(id BIGINT AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB");
        feed.execute("CREATE TABLE market_data_stream_event(id BIGINT,symbol VARCHAR(30),symbol_sequence BIGINT,interval_code VARCHAR(10),candle_open_time TIMESTAMP(6),candle_close_time TIMESTAMP(6),closed BOOLEAN,observed_at TIMESTAMP(6),received_at TIMESTAMP(6),created_at TIMESTAMP(6),price DECIMAL(30,12),source VARCHAR(30),classification VARCHAR(40)) ENGINE=InnoDB");
        source=mock(SharedMarketSource.class);when(source.enabled()).thenReturn(true);when(source.mode()).thenReturn("LIVE");when(source.feedReader()).thenReturn(feed);
        coins=mock(CoinConfigurationService.class);when(coins.enabledSymbols()).thenReturn(List.of("BTCUSDT"));
        protect=mock(LivePositionProtectionService.class);observer=mock(PriceMoveMonitorService.class);
        worker=mock(CandleClosedAnalysisWorker.class);when(worker.processShared(any(),any(),anyBoolean(),anyLong())).thenReturn("COMPLETED");
        now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        local.update("INSERT INTO shared_market_consumer_state(symbol,cutover_sequence,cutover_at,discovered_sequence) VALUES('BTCUSDT',0,?,0)",Timestamp.from(now.minusSeconds(60)));
        local.update("UPDATE shared_market_consumer_state SET status='READY',cutover_source='OPERATOR_APPROVED',approved_by='test',approved_at=CURRENT_TIMESTAMP(6),approval_reference='controlled-test',approved_sequence=cutover_sequence,approved_cutover_at=cutover_at");
        local.update("INSERT INTO shared_market_cutover_approval(symbol,cutover_sequence,cutover_at,approved_by,approved_at,approval_reference) SELECT symbol,cutover_sequence,cutover_at,approved_by,approved_at,approval_reference FROM shared_market_consumer_state");
        local.execute("CREATE TABLE trade_signal(id BIGINT PRIMARY KEY,symbol VARCHAR(30),interval_code VARCHAR(10),candle_open_time TIMESTAMP(6),UNIQUE(symbol,interval_code,candle_open_time))");
        local.execute("CREATE TABLE wallet_trade(id BIGINT PRIMARY KEY,signal_id BIGINT)");
        for(String migration:List.of("V95__fix_138_analysis_dispatch_ranges.sql","V98__fix_141_reconciliation_scan_index.sql"))
            for(String sql:Files.readString(Path.of("src/main/resources/db/migration",migration)).replaceAll("(?m)^\\s*--.*$", "").split(";"))if(!sql.isBlank())local.execute(sql);
        consumer=newConsumer();fix142Schema();
    }
    @Override int blockedSessions(){return admin.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits",Integer.class);}
    @Override void fix142Schema() throws Exception {
        for(String sql:Files.readString(Path.of("src/main/resources/db/migration/V99__fix_142_delivery_ownership.sql")).replaceAll("(?m)^\\s*--.*$", "").split(";"))if(!sql.isBlank())local.execute(sql);
        // FIX-150: production claims now require durable pre-business entry evidence.
        for(String sql:Files.readString(Path.of("src/main/resources/db/migration/V104__fix_150_protection_entry_evidence.sql")).replaceAll("(?m)^\\s*--.*$", "").split(";"))if(!sql.isBlank())local.execute(sql);
        // FIX-151: run the unmodified trader-local generated-column/index migration.
        for(String sql:Files.readString(Path.of("src/main/resources/db/migration/V105__fix_151_delivery_selection_indexes.sql")).replaceAll("(?m)^\\s*--.*$", "").split(";"))if(!sql.isBlank())local.execute(sql);
        local.execute("ALTER TABLE signal_processing_work ADD completed_at TIMESTAMP(6)");
        org.springframework.test.util.ReflectionTestUtils.setField(consumer,"fix142Enabled",true);
    }
    @Override @AfterEach void cleanup() {
        if(consumer!=null)super.cleanup();
        if(admin!=null) {
            if(localSchema!=null)admin.execute("DROP DATABASE IF EXISTS "+localSchema);
            if(sourceSchema!=null)admin.execute("DROP DATABASE IF EXISTS "+sourceSchema);
        }
    }
}
