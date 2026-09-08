package com.crypto.wallet;

import com.crypto.wallet.domain.WalletTrade;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** FIX-126: old limit fails, production migration preserves existing and long
 * Unicode audit text. H2 MySQL mode is not a substitute for a MySQL rollout test. */
class Fix126ExecutionMessageTest {
    @Test void migrationPreservesFullBuySellMessagesAndExistingRows() throws Exception {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE wallet_trade(id BIGINT PRIMARY KEY, execution_message VARCHAR(1000))");
        jdbc.update("INSERT INTO wallet_trade VALUES(1,?)","existing audit");
        String longMessage = "Execution Intelligence — إشارة BUY 🚀 ".repeat(100);
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("INSERT INTO wallet_trade VALUES(2,?)",longMessage));
        String sql;
        try(var input = getClass().getResourceAsStream("/db/migration/V85__fix_126_wallet_execution_message.sql")) {
            assertNotNull(input);
            sql = new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        // H2 has no MySQL collation support. Execute the same type/nullability change.
        jdbc.execute(sql.replace("CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci", ""));
        jdbc.update("INSERT INTO wallet_trade VALUES(2,?)",longMessage);
        jdbc.update("INSERT INTO wallet_trade VALUES(3,?)",longMessage.replace("BUY","SELL"));
        jdbc.execute("INSERT INTO wallet_trade VALUES(4,NULL)");
        assertEquals("existing audit",jdbc.queryForObject("SELECT execution_message FROM wallet_trade WHERE id=1",String.class));
        assertEquals(longMessage,jdbc.queryForObject("SELECT execution_message FROM wallet_trade WHERE id=2",String.class));
        assertEquals(longMessage.replace("BUY","SELL"),jdbc.queryForObject("SELECT execution_message FROM wallet_trade WHERE id=3",String.class));
        assertNull(jdbc.queryForObject("SELECT execution_message FROM wallet_trade WHERE id=4",String.class));
        assertEquals("MEDIUMTEXT",WalletTrade.class.getDeclaredField("executionMessage").getAnnotation(Column.class).columnDefinition());
    }
}
