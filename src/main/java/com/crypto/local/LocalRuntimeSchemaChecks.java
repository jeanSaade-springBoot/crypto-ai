package com.crypto.local;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.time.*;

/** FIX-153 automatic status resolution requires transactional storage; fail startup
 * before socket admission if this foundational contract cannot be established. No row locks added. */
@Component
@DependsOn({"entityManagerFactory","localTraderInstance"})
public class LocalRuntimeSchemaChecks {
    public LocalRuntimeSchemaChecks(JdbcTemplate shared,Environment env){
        if(!"OFF".equals(env.getProperty("shared-market.mode","OFF")))return;
        var jdbc=new JdbcTemplate(Objects.requireNonNull(shared.getDataSource()));jdbc.setQueryTimeout(2);
        String actual=jdbc.queryForObject("SELECT DATABASE()",String.class);
        String expected=env.getProperty("local-candle.schema","crypto_ai");
        if(!expected.equals(actual))throw new IllegalStateException("FIX-153 local runtime schema mismatch: expected="+expected+", actual="+actual);
        var nonTransactional=jdbc.queryForList("SELECT table_name,engine FROM information_schema.tables WHERE table_schema=? AND table_type='BASE TABLE' AND (engine IS NULL OR engine<>'InnoDB')",actual);
        if(!nonTransactional.isEmpty())throw new IllegalStateException("FIX-153 requires InnoDB for atomic effects/outcome; nontransactional local tables="+nonTransactional);
        String zone=jdbc.queryForObject("SELECT @@session.time_zone",String.class);
        if(!Set.of("+00:00","UTC").contains(zone))throw new IllegalStateException("FIX-153 requires an explicit UTC MySQL session, got "+zone);
        Long applied=jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='106' AND success=1",Long.class);
        if(applied==null||applied!=1)throw new IllegalStateException("FIX-153 V106 is not installed successfully");
        Instant now=Instant.now();LoggerFactory.getLogger(getClass()).info("[FIX-153][LOCAL_STORAGE_CONTRACT_CHECKED] schema={}, engine=InnoDB, sessionTimezone={}, migration=106, observedUtc={}, observedKsa={}; storage checks alone do not certify wallet crash/load parity",actual,zone,now,now.atOffset(ZoneOffset.ofHours(3)));
    }
}
