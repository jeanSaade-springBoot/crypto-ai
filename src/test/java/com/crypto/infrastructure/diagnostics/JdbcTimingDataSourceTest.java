package com.crypto.infrastructure.diagnostics;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

/** FIX-142: actual JDBC semantics, not mocked successful timings. */
class JdbcTimingDataSourceTest {
    private JdbcTimingDataSource source() {
        JdbcDataSource raw=new JdbcDataSource();raw.setURL("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        return new JdbcTimingDataSource(raw,"timing-test",250);
    }
    @Test void insertsSelectsAndTwoTransactionsKeepTheirJdbcSemantics() throws Exception {
        try(var ds=source();var c=ds.getConnection();var statement=c.createStatement()) {
            statement.execute("CREATE TABLE timing_fixture(id INT PRIMARY KEY,value_text VARCHAR(50))");
            c.setAutoCommit(false);
            try(var insert=c.prepareStatement("INSERT INTO timing_fixture VALUES(?,?)")) {
                assertSame(c,insert.getConnection());
                insert.setInt(1,1);insert.setString(2,"private-value");assertEquals(1,insert.executeUpdate());
            }
            c.commit(); // The next execution is another transaction, despite autoCommit still false.
            statement.executeUpdate("INSERT INTO timing_fixture VALUES(2,'second')");c.rollback();
            try(var select=c.prepareStatement("SELECT id FROM timing_fixture ORDER BY id");var rows=select.executeQuery()) {
                assertTrue(rows.next());assertEquals(1,rows.getInt(1));assertFalse(rows.next());
            }
            c.commit();assertFalse(c.getAutoCommit());
        }
        assertFalse(JdbcTimingDataSource.statistics().isEmpty());
    }
    @Test void savepointRollbackDoesNotEndOuterTransactionAndBatchWorks() throws Exception {
        try(var ds=source();var c=ds.getConnection();var s=c.createStatement()) {
            s.execute("CREATE TABLE timing_fixture(id INT PRIMARY KEY)");c.setAutoCommit(false);
            s.executeUpdate("INSERT INTO timing_fixture VALUES(1)");var savepoint=c.setSavepoint();
            s.executeUpdate("INSERT INTO timing_fixture VALUES(2)");c.rollback(savepoint);
            try(var batch=c.prepareStatement("INSERT INTO timing_fixture VALUES(?)")) {
                batch.setInt(1,3);batch.addBatch();batch.setInt(1,4);batch.addBatch();assertEquals(2,batch.executeBatch().length);
            }
            c.commit();
            try(var rows=s.executeQuery("SELECT COUNT(*) FROM timing_fixture")){assertTrue(rows.next());assertEquals(3,rows.getInt(1));}
        }
    }
    @Test void originalSqlExceptionAndWrapperContractSurvive() throws Exception {
        try(var ds=source();var c=ds.getConnection();var s=c.createStatement()) {
            assertSame(c,c.unwrap(Connection.class));assertTrue(c.isWrapperFor(Connection.class));
            assertSame(s,s.unwrap(Statement.class));
            SQLException error=assertThrows(SQLException.class,()->s.executeQuery("SELECT * FROM does_not_exist"));
            assertNotNull(error.getSQLState());assertFalse(error instanceof SQLTimeoutException);
        }
    }
    @Test void templateRedactionHandlesEscapesLiteralsAndComments() {
        String sql="SELECT * FROM trade_signal WHERE symbol='private' AND price=123.45 /* secret */ -- hidden\n AND note='can\\'t-leak'";
        String template=JdbcTimingDataSource.safeTemplate(sql);
        assertTrue(template.contains("trade_signal"));assertFalse(template.contains("private"));assertFalse(template.contains("secret"));
        assertFalse(template.contains("leak"));assertFalse(template.contains("hidden"));assertFalse(template.contains("123.45"));
    }
    private long samples(String category) {
        return JdbcTimingDataSource.statistics().entrySet().stream()
            .filter(entry->entry.getKey().contains(category)&&entry.getValue() instanceof java.util.Map<?,?>)
            .mapToLong(entry->((Number)((java.util.Map<?,?>)entry.getValue()).get("count")).longValue()).sum();
    }
    @Test void implicitStatementCloseCompletesFetchAndPreservesDecoratedObjectGraph() throws Exception {
        // Applications may rely on Statement.close rather than explicitly closing ResultSet.
        try(var ds=source();var c=ds.getConnection()) {
            var statement=c.createStatement();long before=samples(":FETCH:");
            var rows=statement.executeQuery("SELECT 1 UNION ALL SELECT 2");
            assertSame(statement,rows.getStatement());assertSame(c,rows.getStatement().getConnection());
            assertSame(rows,rows.unwrap(ResultSet.class));assertTrue(rows.next());
            statement.close();assertTrue(rows.isClosed());assertEquals(before+1,samples(":FETCH:"));
            rows.close();assertEquals(before+1,samples(":FETCH:"),"explicit close must not emit a duplicate end");
        }
    }
    @Test void autocommitTransitionCommitsExistingTransactionAndNextWorkStillExecutes() throws Exception {
        try(var ds=source();var c=ds.getConnection();var statement=c.createStatement()) {
            statement.execute("CREATE TABLE autocommit_fixture(id INT PRIMARY KEY)");
            c.setAutoCommit(false);statement.executeUpdate("INSERT INTO autocommit_fixture VALUES(1)");
            long before=samples("resolution=COMMITTED_BY_AUTOCOMMIT_CHANGE");
            c.setAutoCommit(true);assertEquals(before+1,samples("resolution=COMMITTED_BY_AUTOCOMMIT_CHANGE"));
            statement.executeUpdate("INSERT INTO autocommit_fixture VALUES(2)");
            try(var rows=statement.executeQuery("SELECT COUNT(*) FROM autocommit_fixture")) {
                assertTrue(rows.next());assertEquals(2,rows.getInt(1));
            }
        }
    }
}
