package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** FIX-149: source-window indexes in the confirmed collector schema crypto_ai_v2.
 * Trader Flyway history remains in crypto_ai on this same MySQL server.
 * Java migration avoids temporary stored procedures and their extra privileges.
 * Existing manually-created indexes are accepted only with the exact definition. */
public class V103__fix_149_fresh_admission_indexes extends BaseJavaMigration {
    private static final System.Logger LOG = System.getLogger("FIX149.SourceIndexes");
    private static final String SOURCE_SCHEMA = "crypto_ai_v2";
    private static final String TABLE = "market_data_stream_event";
    private static final String OBSERVED = "idx_stream_fresh_observed";
    private static final String CLOSED = "idx_stream_fresh_closed";
    private static final List<String> OBSERVED_PARTS = List.of(
        "symbol:A", "interval_code:A", "source:A", "classification:A", "observed_at:A", "symbol_sequence:A");
    private static final List<String> CLOSED_PARTS = List.of(
        "symbol:A", "interval_code:A", "closed:A", "source:A", "classification:A", "candle_close_time:D", "symbol_sequence:A");

    // MySQL online DDL commits implicitly; never claim transaction rollback can undo it.
    @Override public boolean canExecuteInTransaction() { return false; }

    @Override public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!"MySQL".equals(connection.getMetaData().getDatabaseProductName())
                || connection.getMetaData().getDatabaseMajorVersion() < 8) {
            throw new SQLException("FIX-149 requires MySQL 8 with descending-index support");
        }
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
            if (!result.next() || !"crypto_ai".equals(result.getString(1))) {
                throw new SQLException("FIX-149 Flyway history must run in crypto_ai; index target is explicitly crypto_ai_v2");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name=? AND engine='InnoDB'")) {
            statement.setString(1, SOURCE_SCHEMA);
            statement.setString(2, TABLE);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new SQLException("FIX-149 requires existing crypto_ai_v2.market_data_stream_event on the trader MySQL server InnoDB table; no empty replacement is created");
                }
            }
        }
        List<String> additions = new ArrayList<>();
        if (!existingIndexMatches(connection, OBSERVED, OBSERVED_PARTS)) {
            additions.add("ADD INDEX " + OBSERVED + " (symbol,interval_code,source,classification,observed_at,symbol_sequence)");
        }
        if (!existingIndexMatches(connection, CLOSED, CLOSED_PARTS)) {
            additions.add("ADD INDEX " + CLOSED + " (symbol,interval_code,closed,source,classification,candle_close_time DESC,symbol_sequence ASC)");
        }
        if (additions.isEmpty()) {
            LOG.log(System.Logger.Level.INFO, "[FIX-149][INDEXES_ALREADY_PRESENT] schema=crypto_ai_v2 historySchema=crypto_ai utc=" + Instant.now());
            return;
        }
        long previousLockWait;
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT @@SESSION.lock_wait_timeout")) {
            if (!result.next()) throw new SQLException("FIX-149 cannot read metadata-lock timeout");
            previousLockWait = result.getLong(1);
        }
        Instant start = Instant.now();
        long startNanos = System.nanoTime();
        LOG.log(System.Logger.Level.INFO, "[FIX-149][INDEX_BUILD_START] schema=crypto_ai_v2 historySchema=crypto_ai table=" + TABLE + " startUtc=" + start);
        // One ALTER builds both missing indexes. Ten seconds bounds metadata-lock wait,
        // not the build duration, resource use, or every possible InnoDB wait.
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET SESSION lock_wait_timeout=10");
            try {
                statement.execute("ALTER TABLE `" + SOURCE_SCHEMA + "`.`" + TABLE + "`" + " " + String.join(", ", additions) + ", ALGORITHM=INPLACE, LOCK=NONE");
                LOG.log(System.Logger.Level.INFO, "[FIX-149][INDEX_BUILD_END] startUtc=" + start + " endUtc=" + Instant.now()
                    + " durationMs=" + ((System.nanoTime() - startNanos) / 1_000_000) + " outcome=SUCCESS");
            } catch (SQLException failure) {
                LOG.log(System.Logger.Level.ERROR, "[FIX-149][INDEX_BUILD_END] startUtc=" + start + " endUtc=" + Instant.now()
                    + " durationMs=" + ((System.nanoTime() - startNanos) / 1_000_000) + " outcome=FAILED sqlState=" + failure.getSQLState());
                throw failure;
            } finally {
                statement.execute("SET SESSION lock_wait_timeout=" + previousLockWait);
            }
        }
    }

    static boolean existingIndexMatches(Connection connection, String name, List<String> expected) throws SQLException {
        List<String> actual = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT column_name,collation,sub_part,non_unique,index_type,is_visible FROM information_schema.statistics "
                + "WHERE table_schema=? AND table_name=? AND index_name=? ORDER BY seq_in_index")) {
            statement.setString(1, SOURCE_SCHEMA);
            statement.setString(2, TABLE);
            statement.setString(3, name);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (result.getObject("sub_part") != null || result.getInt("non_unique") != 1
                            || !"BTREE".equals(result.getString("index_type")) || !"YES".equals(result.getString("is_visible"))) {
                        throw new SQLException("FIX-149 incompatible existing index " + name + "; inspect before deployment");
                    }
                    actual.add(result.getString("column_name") + ":" + result.getString("collation"));
                }
            }
        }
        if (actual.isEmpty()) return false;
        if (!actual.equals(expected)) throw new SQLException("FIX-149 incompatible existing index " + name + "; expected " + expected + " found " + actual);
        return true;
    }
}
