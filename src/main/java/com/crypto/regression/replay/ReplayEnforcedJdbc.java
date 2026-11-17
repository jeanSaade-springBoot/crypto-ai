package com.crypto.regression.replay;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Replay SQL gateway: every statement declares its table and operation before JDBC is reached. */
@Component
@RequiredArgsConstructor
public final class ReplayEnforcedJdbc {
    private final JdbcTemplate jdbc;
    public List<Map<String,Object>> rows(String table, ReplayAccessPolicy.Operation operation, String sql, Object... args) {
        ReplayAccessPolicy.require(table, operation); return jdbc.queryForList(sql, args);
    }
    public Map<String,Object> row(String table, ReplayAccessPolicy.Operation operation, String sql, Object... args) {
        ReplayAccessPolicy.require(table, operation); return jdbc.queryForMap(sql, args);
    }
    public int update(String table, ReplayAccessPolicy.Operation operation, String sql, Object... args) {
        ReplayAccessPolicy.require(table, operation); return jdbc.update(sql, args);
    }
    public <T> List<T> query(String table, ReplayAccessPolicy.Operation operation, String sql, RowMapper<T> mapper, Object... args) {
        ReplayAccessPolicy.require(table, operation); return jdbc.query(sql, mapper, args);
    }
}
