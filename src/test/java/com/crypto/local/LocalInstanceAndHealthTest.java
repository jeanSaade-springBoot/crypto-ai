package com.crypto.local;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalInstanceAndHealthTest {
    @TempDir Path temp;
    @Test void duplicateInstanceCannotStartUntilOriginalExits()throws Exception{
        var env=new MockEnvironment().withProperty("local-candle.instance-lock-path",temp.resolve("instance.lock").toString());
        var first=new LocalTraderInstance(env);
        try{first.shuttingDown();assertThrows(RuntimeException.class,()->new LocalTraderInstance(env));}finally{first.close();}
        new LocalTraderInstance(env).close();
    }
    @Test void instanceLockPathMustBeExplicitAndAbsolute(){
        assertThrows(IllegalStateException.class,()->new LocalTraderInstance(new MockEnvironment()));
        assertThrows(IllegalStateException.class,()->new LocalTraderInstance(new MockEnvironment()
            .withProperty("local-candle.instance-lock-path","relative.lock")));
    }
    @Test void offHealthReturnsBeforeRetiredTablesAreTouched(){
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID(),"sa",""));
        var local=mock(LocalCandleHealth.class);when(local.snapshot()).thenReturn(Map.of("status","LOCAL_STAGE_OBSERVATIONS"));
        var service=new com.crypto.shared.Fix142HealthService(jdbc,mock(com.crypto.administration.service.CoinConfigurationService.class));
        ReflectionTestUtils.setField(service,"localHealth",local);
        assertEquals("LOCAL_STAGE_OBSERVATIONS",service.snapshot().get("status"));verify(local).snapshot();
        // Database has no shared tables: any attempted retired query would fail.
    }
}
