package com.crypto.shared;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** FIX-132: separate SELECT-only pool; never register a second DataSource bean
 * that would accidentally replace Boot's local Flyway/JPA datasource. */
@Component
public class SharedMarketSource {
    private final boolean enabled;
    private final String mode;
    private final JdbcTemplate local;
    private final JdbcTemplate reader;
    private final HikariDataSource pool;
    public SharedMarketSource(JdbcTemplate local, Environment env) {
        mode = env.getProperty("shared-market.mode", "OFF").trim();
        if (!java.util.Set.of("OFF","OBSERVE","LIVE").contains(mode)) throw new IllegalArgumentException("FIX-132 invalid shared-market.mode");
        this.local=local; enabled="LIVE".equals(mode);
        if ("OFF".equals(mode)) { reader = local; pool = null; return; }
        HikariConfig c = new HikariConfig();
        c.setJdbcUrl(env.getRequiredProperty("shared-market.jdbc-url"));
        c.setUsername(env.getRequiredProperty("shared-market.username"));
        c.setPassword(env.getRequiredProperty("shared-market.password"));
        c.setMaximumPoolSize(3); c.setMinimumIdle(0); c.setReadOnly(true);
        c.setConnectionTimeout(5000); c.setValidationTimeout(2000); c.setConnectionInitSql("SET time_zone = '+00:00'");
        c.setPoolName("fix132-source-read");
        // Finite driver waits as well as pool/query waits; no single overall deadline is claimed.
        c.addDataSourceProperty("connectTimeout", "2000");
        c.addDataSourceProperty("socketTimeout", "5000");
        c.addDataSourceProperty("connectionTimeZone","UTC");
        c.addDataSourceProperty("forceConnectionTimeZoneToSession","true");
        pool = new HikariDataSource(c); reader = new JdbcTemplate(pool); reader.setQueryTimeout(5);
        try {
            if(!"crypto_ai_v2".equals(reader.queryForObject("SELECT DATABASE()",String.class)))
                throw new IllegalStateException("FIX-132 shared connection must target crypto_ai_v2");
        } catch(RuntimeException failure) { pool.close();throw failure; }
    }
    public boolean enabled() { return enabled; }
    public JdbcTemplate reader() { return enabled ? reader : local; }
    public JdbcTemplate feedReader() { return reader; }
    /** Instantaneous pool pressure, not a per-query connection-wait measurement. */
    public java.util.Map<String,Integer> poolPressure() {
        if(pool==null || pool.getHikariPoolMXBean()==null)return java.util.Map.of();
        var bean=pool.getHikariPoolMXBean();
        return java.util.Map.of("active",bean.getActiveConnections(),"idle",bean.getIdleConnections(),"waiting",bean.getThreadsAwaitingConnection());
    }
    public String mode() { return mode; }
    public void requireLocalWriter() {
        if (enabled) throw new IllegalStateException("FIX-132: collector owns candle writes; Trader import is disabled");
    }
    @PreDestroy public void close() { if (pool != null) pool.close(); }
}
