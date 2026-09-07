package com.crypto.execution.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** FIX-122 database tests: committed visibility, rollback, and fresh-instance recovery.
 * H2 validates the provider contract, not MySQL deadlock behavior or wallet serialization.
 */
class StopLossEvidenceStoreTest {
    DriverManagerDataSource ds; DataSourceTransactionManager manager; JdbcTemplate jdbc;
    StopLossEvidenceStore store;
    final Instant at=Instant.parse("2026-09-07T14:05:28Z");
    @BeforeEach void setup() {
        ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        manager=new DataSourceTransactionManager(ds);jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE wallet_trade(id BIGINT PRIMARY KEY,symbol VARCHAR(30),status VARCHAR(20),side VARCHAR(10),execution_reason VARCHAR(40),executed_at TIMESTAMP(6))");
        store=new StopLossEvidenceStore(ds,manager);
    }
    void insert(long id,String symbol,String status,String side,String reason,Instant time) {
        jdbc.update("INSERT INTO wallet_trade VALUES(?,?,?,?,?,?)",id,symbol,status,side,reason,Timestamp.from(time));
    }
    @Test void ignoresOtherSymbolsPartialStopsFailedOrdersAndFutureRows() {
        insert(1,"BICOUSDT","EXECUTED","SELL","STOP_LOSS",at);
        insert(2,"DOGEUSDT","EXECUTED","SELL","STOP_LOSS",at.plusSeconds(1));
        insert(3,"BICOUSDT","EXECUTED","SELL","NEAR_TP_PARTIAL_HARVEST",at.plusSeconds(1));
        insert(4,"BICOUSDT","REJECTED","SELL","STOP_LOSS",at.plusSeconds(1));
        insert(5,"BICOUSDT","EXECUTED","SELL","POSITION_STOP_LOSS",at.plusSeconds(1));
        insert(6,"BICOUSDT","EXECUTED","SELL","STOP_LOSS",at.plusSeconds(10));
        assertEquals(1,store.latest("BICOUSDT",at.plusSeconds(2)).executionId());
    }
    @Test void recreatedProviderRecoversLatestCommittedStop() {
        insert(1,"BICOUSDT","EXECUTED","SELL","STOP_LOSS",at);
        var restarted=new StopLossEvidenceStore(ds,manager);
        assertEquals(new StopLossEvidencePolicy.Boundary(1,at),restarted.latest("BICOUSDT",at));
    }
    @Test void uncommittedAndRolledBackStopDoNotBecomeBoundaries() {
        new TransactionTemplate(manager).executeWithoutResult(tx->{
            insert(1,"BICOUSDT","EXECUTED","SELL","STOP_LOSS",at);
            assertNull(store.latest("BICOUSDT",at));
            tx.setRollbackOnly();
        });
        assertNull(store.latest("BICOUSDT",at));
        new TransactionTemplate(manager).executeWithoutResult(tx->insert(2,"BICOUSDT","EXECUTED","SELL","STOP_LOSS",at));
        assertEquals(2,store.latest("BICOUSDT",at).executionId());
    }
}
