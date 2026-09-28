package com.crypto.shared;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CandleInputAuditTransactionTest {
    @Test void liveAndReplayAuditCommitIndependentlyOfReadOnlyCaller() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE shared_candle_input_audit(context_key VARCHAR(100),read_index INT,input_hash VARCHAR(64),row_count INT,query_text VARCHAR(1000))");
        var manager=new JdbcTransactionManager(ds);
        var outer=new TransactionTemplate(manager); outer.setReadOnly(true);
        for(String context:List.of("LIVE:1","REPLAY:1")) {
            outer.executeWithoutResult(tx->{
                var connection=DataSourceUtils.getConnection(ds);
                assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                try(var audit=CandleInputAudit.open(jdbc,manager,context)) {
                    CandleInputAudit.capture("SELECT candles",List.of());
                }
                assertSame(connection,DataSourceUtils.getConnection(ds));
                assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                tx.setRollbackOnly();
            });
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM shared_candle_input_audit WHERE context_key=?",Integer.class,context));
        }
        CandleInputAudit.capture("No active scope",List.of());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM shared_candle_input_audit",Integer.class));
    }
}
