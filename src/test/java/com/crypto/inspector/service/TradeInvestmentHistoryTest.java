package com.crypto.inspector.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** FIX-121: historical reporting must not infer acquisition cost from current wallet state or P/L. */
class TradeInvestmentHistoryTest {
    private static final Instant START = Instant.parse("2026-09-07T13:25:08Z");
    private TradeInvestmentHistory.Leg leg(long id, String side, String quantity, String price) {
        return new TradeInvestmentHistory.Leg(id, "BICOUSDT", side, START.plusSeconds(id),
                new BigDecimal(quantity), new BigDecimal(price), null, id + 100);
    }
    private void amount(String expected, String actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual)));
    }

    @Test void singleBuyUsesActualExecutionRatherThanSellPrice() {
        var result = TradeInvestmentHistory.closed(List.of(leg(1,"BUY","10","2"), leg(2,"SELL","10","3")),2);
        assertEquals("AVAILABLE", result.status());
        amount("10",result.entryQuantity()); amount("2",result.unitEntryPriceUsdt()); amount("20",result.totalInvestedUsdt());
    }

    @Test void addsUseAllAcquiredQuantityAndWeightedCost() {
        var result = TradeInvestmentHistory.closed(List.of(leg(1,"BUY","10","2"),leg(2,"BUY","5","5"),leg(3,"SELL","15","4")),3);
        amount("15",result.entryQuantity()); amount("3",result.unitEntryPriceUsdt()); amount("45",result.totalInvestedUsdt());
        assertEquals(2,result.buyCount());
    }

    @Test void partialSellAndLaterAddDoNotReduceCumulativeInvestment() {
        var result = TradeInvestmentHistory.closed(List.of(leg(1,"BUY","10","2"),leg(2,"SELL","5","3"),leg(3,"BUY","5","4"),leg(4,"SELL","10","5")),4);
        amount("15",result.entryQuantity()); amount("40",result.totalInvestedUsdt());
        amount("2.666666666667",result.unitEntryPriceUsdt());
    }

    @Test void aPartialSellIsNotACompletedPosition() {
        assertEquals("UNAVAILABLE",TradeInvestmentHistory.closed(List.of(leg(1,"BUY","10","2"),leg(2,"SELL","5","3")),2).status());
    }

    @Test void laterSameSymbolPositionCannotPolluteHistoricalTrade() {
        var legs=List.of(leg(1,"BUY","10","2"),leg(2,"SELL","10","3"),leg(3,"BUY","100","8"),leg(4,"SELL","100","9"));
        amount("20",TradeInvestmentHistory.closed(legs,2).totalInvestedUsdt());
        amount("800",TradeInvestmentHistory.closed(legs,4).totalInvestedUsdt());
    }

    @Test void equalTimestampsUseExecutionIdAndNotListOrder() {
        var buy=leg(1,"BUY","10","2");
        var sell=new TradeInvestmentHistory.Leg(2,"BICOUSDT","SELL",buy.time(),new BigDecimal("10"),new BigDecimal("3"),null,null);
        amount("20",TradeInvestmentHistory.closed(List.of(sell,buy),2).totalInvestedUsdt());
    }

    @Test void missingBuyAndOverdrawnLedgerAreUnavailable() {
        assertEquals("UNAVAILABLE",TradeInvestmentHistory.closed(List.of(leg(2,"SELL","10","3")),2).status());
        assertEquals("UNAVAILABLE",TradeInvestmentHistory.closed(List.of(leg(1,"BUY","5","2"),leg(2,"SELL","10","3")),2).status());
    }

    @Test void duplicateExecutionsAreNotDoubleCounted() {
        var buy=leg(1,"BUY","10","2");
        assertEquals("UNAVAILABLE",TradeInvestmentHistory.closed(List.of(buy,buy,leg(2,"SELL","20","3")),2).status());
    }

    @Test void highQuantityLowPriceRetainsDecimalPrecision() {
        var result=TradeInvestmentHistory.closed(List.of(leg(1,"BUY","136332841.976742454696","0.0000037"),leg(2,"SELL","136332841.976742454696","0.0000038")),2);
        assertEquals("136332841.976742454696",result.entryQuantity());
        assertEquals("0.0000037",result.unitEntryPriceUsdt());
        amount(new BigDecimal("136332841.976742454696").multiply(new BigDecimal("0.0000037")).toPlainString(),result.totalInvestedUsdt());
    }

    @Test void persistedGrossCostIsAuthoritativeAndExcludesFees() {
        var buy=new TradeInvestmentHistory.Leg(1,"BICOUSDT","BUY",START,new BigDecimal("3"),new BigDecimal("0.333333333333"),BigDecimal.ONE,101L);
        amount("1",TradeInvestmentHistory.closed(List.of(buy,leg(2,"SELL","3","0.4")),2).totalInvestedUsdt());
    }

    @Test void negativeBuyCostIsUnavailable() {
        var buy=new TradeInvestmentHistory.Leg(1,"BICOUSDT","BUY",START,BigDecimal.TEN,BigDecimal.ONE,BigDecimal.ONE.negate(),101L);
        assertEquals("UNAVAILABLE",TradeInvestmentHistory.closed(List.of(buy,leg(2,"SELL","10","2")),2).status());
    }

    @Test void tinyPartialSaleIsNeverRoundedIntoACompleteExit() {
        assertEquals("UNAVAILABLE",TradeInvestmentHistory.closed(List.of(
                leg(1,"BUY","0.000000000004","100"),leg(2,"SELL","0.000000000002","101")),2).status());
    }

    private Map<String,Object> row(long id,String side,String qty,String price) {
        Map<String,Object> row=new HashMap<>();
        row.put("id",id);row.put("symbol","BICOUSDT");row.put("side",side);
        row.put("execution_time",Timestamp.from(START.plusSeconds(id)));
        row.put("quantity",new BigDecimal(qty));row.put("execution_price",new BigDecimal(price));row.put("signal_id",id+100);
        return row;
    }

    @Test void openPositionMustMatchInitialSignalAndRemainingQuantity() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),eq("BICOUSDT"))).thenReturn(List.of(row(1,"BUY","10","2"),row(2,"SELL","5","3"),row(3,"BUY","5","4")));
        var history=new TradeInvestmentHistory(jdbc);
        amount("15",history.openWallet("BICOUSDT",101L,BigDecimal.TEN).entryQuantity());
        assertEquals("UNAVAILABLE",history.openWallet("BICOUSDT",103L,BigDecimal.TEN).status());
        assertEquals("UNAVAILABLE",history.openWallet("BICOUSDT",101L,BigDecimal.ONE).status());
        // Request-local caching performs one ledger read; no wallet writes occur.
        verify(jdbc,times(1)).queryForList(anyString(),eq("BICOUSDT"));
        verifyNoMoreInteractions(jdbc);
    }

    @Test void closedHistoryDoesNotReadZeroedManagedPositionOrCurrentWallet() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),eq("BICOUSDT"))).thenReturn(List.of(row(1,"BUY","10","2"),row(2,"SELL","10","3")));
        amount("20",new TradeInvestmentHistory(jdbc).closedWallet("BICOUSDT",2).totalInvestedUsdt());
        verify(jdbc).queryForList(contains("FROM wallet_trade"),eq("BICOUSDT"));
        verifyNoMoreInteractions(jdbc);
    }

    @Test void resolvedProvenCopyLegsExcludeEarlierAndLaterSameSymbolPositions() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),eq("BICOUSDT"))).thenReturn(List.of(
                row(1,"BUY","2","1"),row(2,"SELL","2","2"),
                row(3,"BUY","10","2"),row(4,"BUY","5","5"),row(5,"SELL","15","4"),
                row(6,"BUY","100","8"),row(7,"SELL","100","9")));
        assertEquals(List.of(3L,4L,5L),new TradeInvestmentHistory(jdbc).closedWalletLegs("BICOUSDT",5)
                .stream().map(TradeInvestmentHistory.Leg::id).toList());
    }

    private Map<String,Object> replayPosition() {
        Map<String,Object> p=new HashMap<>();p.put("id",91L);p.put("test_run_id",77L);p.put("symbol","BICOUSDT");
        p.put("entry_time",Timestamp.from(START.plusSeconds(1)));p.put("exit_time",Timestamp.from(START.plusSeconds(3)));
        return p;
    }

    @Test void replayUsesRunScopedExecutionsAndMatchesProductionTotals() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        var rows=List.of(row(1,"BUY","10","2"),row(2,"BUY","5","5"),row(3,"SELL","15","4"));
        when(jdbc.queryForList(contains("FROM wallet_execution_test WHERE"),eq(77L),eq("BICOUSDT"))).thenReturn(rows);
        when(jdbc.queryForList(contains("FROM wallet_trade"),eq("BICOUSDT"))).thenReturn(rows);
        var history=new TradeInvestmentHistory(jdbc);
        assertEquals(history.closedWallet("BICOUSDT",3),history.replay(replayPosition(),null));
        verify(jdbc).queryForList(contains("test_run_id=? AND symbol=?"),eq(77L),eq("BICOUSDT"));
    }

    @Test void archiveUsesBatchAndRunIdentity() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM wallet_execution_test_archive"),eq(8L),eq(77L),eq("BICOUSDT")))
                .thenReturn(List.of(row(1,"BUY","10","2"),row(2,"BUY","5","5"),row(3,"SELL","15","4")));
        amount("45",new TradeInvestmentHistory(jdbc).replay(replayPosition(),8L).totalInvestedUsdt());
        verify(jdbc).queryForList(contains("archive_batch_id=? AND test_run_id=?"),eq(8L),eq(77L),eq("BICOUSDT"));
        verifyNoMoreInteractions(jdbc);
    }

    @Test void missingReplayExecutionsRemainUnavailable() {
        var history=new TradeInvestmentHistory(mock(JdbcTemplate.class));
        assertEquals("UNAVAILABLE",history.replay(replayPosition(),null).status());
    }

    @Test void provenCanUseSavedExecutionsAfterWalletHistoryDisappears() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM proven_trade_execution_point"),eq("BICOUSDT"),eq(90L)))
                .thenReturn(List.of(row(1,"BUY","10","2"),row(2,"SELL","10","3")));
        Map<String,Object> p=new HashMap<>();p.put("id",90L);p.put("symbol","BICOUSDT");p.put("source_wallet_sell_trade_id",2L);
        new TradeInvestmentHistory(jdbc).enrichProven(List.of(p));
        amount("20",((TradeInvestmentHistory.Investment)p.get("investment")).totalInvestedUsdt());
        verify(jdbc).queryForList(contains("FROM wallet_trade"),eq("BICOUSDT"));
        verify(jdbc).queryForList(contains("FROM proven_trade_execution_point"),eq("BICOUSDT"),eq(90L));
        verifyNoMoreInteractions(jdbc);
    }
}
