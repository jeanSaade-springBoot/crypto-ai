package com.crypto.local;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies packaged Flyway resolution without claiming MySQL DDL execution. */
class BaselineResolutionTest {
    @Test void traderBaselineResolvesAlongsideImmutableVersionedMigrations(){
        var flyway=Flyway.configure().dataSource(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID(),"sa","")).locations("classpath:db/migration").load();
        var all=Arrays.asList(flyway.info().all());
        assertTrue(all.stream().anyMatch(m->m.getScript().equals("B105__fix_153_trader_only_baseline.sql")));
        assertTrue(all.stream().anyMatch(m->m.getVersion()!=null&&m.getVersion().getVersion().equals("106")));
        // info/resolution only: real MySQL migrate/validate lives in the dedicated profile.
    }
}
