package com.crypto.infrastructure.transaction;

import com.crypto.service.BinanceKlineService;
import com.crypto.repository.CandleRepository;
import com.crypto.position.service.LivePositionProtectionService;
import com.crypto.debug.monitor.service.PriceMoveMonitorService;
import com.crypto.market.service.MarketPriceEventService;
import com.crypto.indicator.event.CandleClosedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BinanceKlineTransactionTest {
    @Test void exactPriceCommitsBeforeProtectionAndCloseDispatchFollowsProtection() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE market_price_event(id BIGINT AUTO_INCREMENT PRIMARY KEY,symbol VARCHAR(30),observed_at TIMESTAMP(6),price DECIMAL(30,12),source VARCHAR(40))");
        var manager=new DataSourceTransactionManager(ds);
        var coordinator=new KlineTransactionCoordinator(manager,mock(Fix124ProtectionStore.class));
        var candle=mock(CandleRepository.class);
        var protection=mock(LivePositionProtectionService.class);
        var observer=mock(PriceMoveMonitorService.class);
        List<String> order=new ArrayList<>();
        doAnswer(invocation->{
            // Independent connection can see the exact event: input commit finished.
            try(var connection=ds.getConnection();var statement=connection.createStatement();
                var rows=statement.executeQuery("SELECT COUNT(*) FROM market_price_event")) {
                assertTrue(rows.next());assertEquals(1,rows.getInt(1));
            }
            assertEquals(new BigDecimal("7.046"),invocation.getArgument(1));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCommit(){order.add("protection committed");}
            });
            return null;
        }).when(protection).onPrice(eq("UNIUSDT"),any());
        ApplicationEventPublisher publisher=event->{
            assertInstanceOf(CandleClosedEvent.class,event);
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(List.of("protection committed"),order);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCommit(){order.add("close dispatched");}
            });
        };
        var service=new BinanceKlineService(candle,publisher,protection,observer,new MarketPriceEventService(jdbc),coordinator);
        var message=new ObjectMapper().readTree("""
                {"E":1788846065434,"k":{"s":"UNIUSDT","i":"1m","t":1788846060000,"T":1788846119999,
                "x":true,"o":"7","h":"7.1","l":"6.9","c":"7.046","v":"20","q":"140","n":4,"V":"10","Q":"70"}}
                """);
        assertTrue(service.processKline(message));
        assertEquals(List.of("protection committed","close dispatched"),order);
        assertEquals(Instant.ofEpochMilli(1788846065434L),jdbc.queryForObject("SELECT observed_at FROM market_price_event",java.sql.Timestamp.class).toInstant());
        verify(candle).upsert(eq("UNIUSDT"),eq("1m"),eq(Instant.ofEpochMilli(1788846060000L)),
                eq(Instant.ofEpochMilli(1788846119999L)),any(),any(),any(),eq(new BigDecimal("7.046")),any(),any(),eq(4L),any(),any(),eq(true));
        verify(observer).onPrice(eq("UNIUSDT"),eq(new BigDecimal("7.046")),any());
    }
}
