package com.crypto.shared;

import java.util.*;
import jakarta.persistence.*;
import org.hibernate.cfg.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.support.TransactionTemplate;

/** Standalone real-MySQL regression harness; URL must name an isolated fix136_test schema. */
public class Fix136MySqlScenarios {
    @Entity(name="Fix136Probe") @Table(name="fix136_probe")
    public static class Probe { @Id public Long id; public Probe() {} }
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        String url=System.getenv("FIX136_TEST_MYSQL_URL");
        if(url==null || !url.contains("/fix136_test?"))throw new IllegalArgumentException("Isolated fix136_test schema required");
        var ds=new DriverManagerDataSource(url,"root","");
        var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE shared_candle_input_audit(id BIGINT AUTO_INCREMENT PRIMARY KEY,context_key VARCHAR(100),read_index INT,input_hash VARCHAR(64),row_count INT,query_text VARCHAR(1000))");
        var config=new Configuration().addAnnotatedClass(Probe.class);
        config.getProperties().put("hibernate.connection.datasource",ds);
        config.setProperty("hibernate.hbm2ddl.auto","create-drop");
        config.setProperty("hibernate.connection.handling_mode","DELAYED_ACQUISITION_AND_HOLD");
        try(var emf=config.buildSessionFactory()) {
            var manager=new JpaTransactionManager(emf); manager.setDataSource(ds);
            manager.setJpaDialect(new HibernateJpaDialect()); manager.afterPropertiesSet();
            var read=new TransactionTemplate(manager); read.setReadOnly(true);
            for(String context:List.of("LIVE:100","REPLAY:100")) {
                read.executeWithoutResult(tx -> {
                    var outer=DataSourceUtils.getConnection(ds);
                    try { check(outer.isReadOnly(),"Outer JPA connection must be read-only"); }
                    catch(java.sql.SQLException e) {throw new RuntimeException(e);}
                    try(var audit=CandleInputAudit.open(jdbc,manager,context)) {
                        CandleInputAudit.capture("SELECT closed candles",List.of());
                    }
                    check(DataSourceUtils.getConnection(ds)==outer,"Outer connection restored");
                    try { check(outer.isReadOnly(),"Outer read-only flag retained"); }
                    catch(java.sql.SQLException e) {throw new RuntimeException(e);}
                    tx.setRollbackOnly();
                });
                check(jdbc.queryForObject("SELECT COUNT(*) FROM shared_candle_input_audit WHERE context_key=?",Integer.class,context)==1,"Audit survives outer rollback");
                System.out.println("PASS read-only JPA audit isolation "+context);
            }
            jdbc.execute("RENAME TABLE shared_candle_input_audit TO audit_hidden");
            boolean failed=false;
            try {read.executeWithoutResult(tx->{try(var a=CandleInputAudit.open(jdbc,manager,"LIVE:failure")){CandleInputAudit.capture("SELECT x",List.of());}});}
            catch(RuntimeException expected){failed=true;}
            check(failed,"Audit failure must propagate, never silently lose evidence");
            jdbc.execute("RENAME TABLE audit_hidden TO shared_candle_input_audit");
            CandleInputAudit.capture("outside scope",List.of());
            check(jdbc.queryForObject("SELECT COUNT(*) FROM shared_candle_input_audit",Integer.class)==2,"Failed scope cleaned up");
            System.out.println("PASS audit failure propagates and context is cleaned up");
        }
        System.out.println("RESULT: 3 scenarios passed");
    }
}
