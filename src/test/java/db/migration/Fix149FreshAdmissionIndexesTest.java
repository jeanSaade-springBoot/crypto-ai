package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** FIX-149: do not silently accept a same-name index with the wrong access path. */
class Fix149FreshAdmissionIndexesTest {
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);
    private final ResultSet rows = mock(ResultSet.class);

    private void prepare() throws SQLException {
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
    }
    @Test void missingIndexNeedsCreation() throws Exception {
        prepare(); when(rows.next()).thenReturn(false);
        assertFalse(V103__fix_149_fresh_admission_indexes.existingIndexMatches(connection, "idx_stream_fresh_closed", List.of("candle_close_time:D")));
        verify(statement).setString(1, "crypto_ai_v2");
        verify(statement).setString(2, "market_data_stream_event");
        verify(statement).setString(3, "idx_stream_fresh_closed");
    }
    @Test void exactManuallyCreatedDescendingIndexIsAccepted() throws Exception {
        prepare(); when(rows.next()).thenReturn(true, true, false);
        when(rows.getInt("non_unique")).thenReturn(1);
        when(rows.getString("index_type")).thenReturn("BTREE");
        when(rows.getString("is_visible")).thenReturn("YES");
        when(rows.getString("column_name")).thenReturn("candle_close_time", "symbol_sequence");
        when(rows.getString("collation")).thenReturn("D", "A");
        assertTrue(V103__fix_149_fresh_admission_indexes.existingIndexMatches(connection, "idx_stream_fresh_closed", List.of("candle_close_time:D", "symbol_sequence:A")));
    }
    @Test void ascendingTimeCannotMasqueradeAsDescendingTime() throws Exception {
        prepare(); when(rows.next()).thenReturn(true, false);
        when(rows.getInt("non_unique")).thenReturn(1);
        when(rows.getString("index_type")).thenReturn("BTREE");
        when(rows.getString("is_visible")).thenReturn("YES");
        when(rows.getString("column_name")).thenReturn("candle_close_time");
        when(rows.getString("collation")).thenReturn("A");
        assertThrows(SQLException.class, () -> V103__fix_149_fresh_admission_indexes.existingIndexMatches(connection, "idx_stream_fresh_closed", List.of("candle_close_time:D")));
    }
    @Test void prefixIndexIsRejected() throws Exception {
        prepare(); when(rows.next()).thenReturn(true);
        when(rows.getObject("sub_part")).thenReturn(10);
        assertThrows(SQLException.class, () -> V103__fix_149_fresh_admission_indexes.existingIndexMatches(connection, "idx_stream_fresh_observed", List.of("symbol:A")));
    }
    @Test void uniqueIndexIsRejected() throws Exception {
        prepare(); when(rows.next()).thenReturn(true);
        when(rows.getInt("non_unique")).thenReturn(0);
        assertThrows(SQLException.class, () -> V103__fix_149_fresh_admission_indexes.existingIndexMatches(connection, "idx_stream_fresh_observed", List.of("symbol:A")));
    }
    @Test void onlineDdlDoesNotClaimTransactionalRollback() {
        assertFalse(new V103__fix_149_fresh_admission_indexes().canExecuteInTransaction());
    }
}
