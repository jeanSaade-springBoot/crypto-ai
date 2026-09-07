package com.crypto.inspector.service;

import com.crypto.audit.repository.ProductionExitAuditRepository;
import com.crypto.dto.TradeInspectorTradeView;
import com.crypto.execution.repository.ExecutionOpportunityRepository;
import com.crypto.position.repository.PositionAnalysisRepository;
import com.crypto.position.repository.PositionManagementEventRepository;
import com.crypto.repository.CandleRepository;
import com.crypto.repository.PaperPositionRepository;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.wallet.domain.WalletManagedPosition;
import com.crypto.wallet.domain.WalletTrade;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import com.crypto.wallet.repository.WalletTradeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** FIX-121: verify the real Inspector DTO wiring while preserving legacy P/L and quantity fields. */
class TradeInspectorInvestmentViewTest {
    private static final Instant START=Instant.parse("2026-09-07T13:25:08Z");

    private TradeInspectorService service(JdbcTemplate jdbc) {
        return new TradeInspectorService(mock(WalletTradeRepository.class),mock(CandleRepository.class),
                mock(PaperPositionRepository.class),mock(WalletManagedPositionRepository.class),
                mock(TradeSignalRepository.class),mock(ExecutionOpportunityRepository.class),
                mock(ProductionExitAuditRepository.class),mock(PositionAnalysisRepository.class),
                mock(PositionManagementEventRepository.class),jdbc);
    }

    private Map<String,Object> row(long id,String side,String qty,String price) {
        return Map.of("id",id,"symbol","BICOUSDT","side",side,
                "execution_time",Timestamp.from(START.plusSeconds(id)),"quantity",new BigDecimal(qty),
                "execution_price",new BigDecimal(price),"signal_id",101L);
    }

    @Test void closedCardExposesCumulativeInvestmentWithoutChangingSellQuantityOrPnl() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM wallet_trade"),eq("BICOUSDT"))).thenReturn(List.of(
                row(1,"BUY","10","2"),row(2,"BUY","5","5"),row(3,"SELL","5","4"),row(4,"SELL","10","4")));
        WalletTrade buy=WalletTrade.builder().id(1L).symbol("BICOUSDT").side("BUY").quantity(BigDecimal.TEN)
                .priceUsdt(new BigDecimal("2")).executedAt(START.plusSeconds(1)).build();
        WalletTrade sell=WalletTrade.builder().id(4L).symbol("BICOUSDT").side("SELL").quantity(BigDecimal.TEN)
                .priceUsdt(new BigDecimal("4")).executedAt(START.plusSeconds(4))
                .realizedPnlUsdt(new BigDecimal("7")).realizedPnlPercent(new BigDecimal("12")).build();
        TradeInspectorTradeView view=ReflectionTestUtils.invokeMethod(service(jdbc),"toView",buy,sell);
        assertNotNull(view);
        assertEquals("15",view.investment().entryQuantity());
        assertEquals("45",view.investment().totalInvestedUsdt());
        assertEquals("3",view.investment().unitEntryPriceUsdt());
        assertEquals(BigDecimal.TEN,view.quantity());
        assertEquals(new BigDecimal("7"),view.realizedPnl());
        assertEquals(new BigDecimal("12"),view.realizedPnlPercent());
        // Original chart fill stays an execution price; the Entry block uses the weighted investment field.
        assertEquals(new BigDecimal("2"),view.entryPrice());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void openCardKeepsRemainingQuantitySeparateFromTotalAcquiredQuantity() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM wallet_trade"),eq("BICOUSDT"))).thenReturn(List.of(
                row(1,"BUY","10","2"),row(2,"SELL","5","4")));
        WalletManagedPosition managed=WalletManagedPosition.builder().id(9L).symbol("BICOUSDT")
                .entrySignalId(101L).openedAt(START).quantity(new BigDecimal("5"))
                .averageEntryPriceUsdt(new BigDecimal("2")).build();
        TradeInspectorTradeView view=ReflectionTestUtils.invokeMethod(service(jdbc),"toOpenView",managed,new TradeInvestmentHistory(jdbc));
        assertNotNull(view);
        assertEquals("10",view.investment().entryQuantity());
        assertEquals("20",view.investment().totalInvestedUsdt());
        assertEquals(new BigDecimal("5"),view.quantity());
        assertNull(view.realizedPnl());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
