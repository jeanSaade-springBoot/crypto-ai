package com.crypto.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import com.crypto.config.BtcContextProperties;
import com.crypto.domain.*;
import com.crypto.repository.*;
import com.crypto.service.BtcMarketContextService;
import com.crypto.execution.service.ExecutionReplayScope;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** FIX-142: real InnoDB backlog fixture through admission, phases, dispatcher, a
 * committed signal, and the actual BTC freshness service in Production/Replay.
 * The analysis worker is a persistence fixture; scoring, wallet execution and wide
 * production row/I/O costs are NOT simulated. Full application load remains a release gate. */
class Fix142BacklogMySqlIT extends Fix142DeliveryMySqlIT {
    @Test @Timeout(120) void freshBtcContextAdvancesBehind44738OldDeliveries() throws Exception {
        final int backlog=44738;
        Instant old=now.minusSeconds(172800);
        Timestamp cutover=Timestamp.from(now.minusSeconds(259200));
        local.update("UPDATE shared_market_consumer_state SET cutover_at=?,approved_cutover_at=?",cutover,cutover);
        local.update("UPDATE shared_market_cutover_approval SET cutover_at=?",cutover);
        for(int first=1;first<=backlog;first+=500) {
            final int start=first,size=Math.min(500,backlog-first+1);
            feed.batchUpdate("INSERT INTO market_data_stream_event VALUES(?,'BTCUSDT',?,'1m',?,?,true,?,?,?,10,'LIVE_WEBSOCKET','LIVE')",new BatchPreparedStatementSetter(){
                public int getBatchSize(){return size;}
                public void setValues(PreparedStatement statement,int index)throws SQLException {
                    long id=start+index;statement.setLong(1,id);statement.setLong(2,id);
                    statement.setTimestamp(3,Timestamp.from(old.minusSeconds(60)));statement.setTimestamp(4,Timestamp.from(old));
                    statement.setTimestamp(5,Timestamp.from(old));statement.setTimestamp(6,Timestamp.from(old));statement.setTimestamp(7,Timestamp.from(old));
                }
            });
            // Same immutable source identity, untouched phase-0 backlog; never reset later.
            for(var sourceRow:feed.queryForList("SELECT * FROM market_data_stream_event WHERE id BETWEEN ? AND ?",start,start+size-1))
                new Fix142DeliveryProtocol(local).insert(sourceRow,"BTCUSDT");
        }
        local.execute("ALTER TABLE trade_signal ADD generated_at TIMESTAMP(6)");
        local.update("INSERT INTO trade_signal(id,symbol,interval_code,candle_open_time,generated_at) VALUES(900001,'BTCUSDT','1m',?,?)",Timestamp.from(old.minusSeconds(60)),Timestamp.from(old));
        var signals=mock(TradeSignalRepository.class);var candles=mock(CandleRepository.class);
        when(candles.findClosedCandles(anyString(),anyString(),any())).thenReturn(List.of());
        when(signals.findTopBySymbolAndIntervalAndCandleOpenTimeLessThanEqualAndGeneratedAtLessThanEqualOrderByCandleOpenTimeDesc(anyString(),anyString(),any(),any())).thenAnswer(invocation->{
        	// Explicit argument types prevent selection of the queryForList overload
        	// that returns scalar Objects instead of row Maps.
        	java.util.List<java.util.Map<String, Object>> rows =
        	    local.queryForList(
        	        "SELECT * FROM trade_signal " +
        	        "WHERE symbol=? AND interval_code=? " +
        	        "AND candle_open_time<=? AND generated_at<=? " +
        	        "ORDER BY candle_open_time DESC LIMIT 1",
        	        invocation.<String>getArgument(0),
        	        invocation.<String>getArgument(1),
        	        Timestamp.from(invocation.<Instant>getArgument(2)),
        	        Timestamp.from(invocation.<Instant>getArgument(3))
        	    );            if(rows.isEmpty())return Optional.empty();var row=rows.getFirst();
            return Optional.of(TradeSignal.builder().id(((Number)row.get("id")).longValue()).symbol("BTCUSDT").interval("1m")
                .candleOpenTime(((Timestamp)row.get("candle_open_time")).toInstant()).generatedAt(((Timestamp)row.get("generated_at")).toInstant()).decision(SignalDecision.NEUTRAL).build());
        });
        var context=new BtcMarketContextService(candles,signals,new BtcContextProperties(true,"BTCUSDT",200,120,null,null,null,true,null,false));
        assertEquals(BtcContextStatus.STALE_CONTEXT,context.evaluate("ETHUSDT","1m",SignalDecision.BUY,true,Instant.now()).contextStatus());
        long fresh=backlog+1;event(fresh,Instant.now());
        Instant close=Instant.now().minusSeconds(1),open=close.minusSeconds(60);
        feed.update("UPDATE market_data_stream_event SET closed=true,candle_open_time=?,candle_close_time=? WHERE id=?",Timestamp.from(open),Timestamp.from(close),fresh);
        when(worker.processShared(any(),any(),eq(true),eq(fresh))).thenAnswer(invocation->{
            new TransactionTemplate(manager).executeWithoutResult(tx->local.update("INSERT INTO trade_signal(id,symbol,interval_code,candle_open_time,generated_at) VALUES(900002,'BTCUSDT','1m',?,CURRENT_TIMESTAMP(6))",Timestamp.from(open)));
            return "COMPLETED";
        });
        long started=System.nanoTime();consumer.discoverFresh();consumer.processNext("BTCUSDT");consumer.dispatchAnalysis();
        long elapsed=(System.nanoTime()-started)/1_000_000;
        long budget=Long.parseLong(System.getenv().getOrDefault("FIX142_TEST_FRESH_BUDGET_MS","2000"));
        assertTrue(elapsed<budget,"precommitted fresh-path budget exceeded: "+elapsed+"ms");
        assertEquals(0L,local.queryForObject("SELECT discovered_sequence FROM shared_market_consumer_state",Long.class));
        assertEquals(backlog,local.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery WHERE source_event_id<=? AND phase=0",Integer.class,backlog));
        assertEquals("COMPLETED",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery WHERE source_event_id=?",String.class,fresh));
        Instant evaluatedAt=Instant.now();var production=context.evaluate("ETHUSDT","1m",SignalDecision.BUY,true,evaluatedAt);
        assertNotEquals(BtcContextStatus.STALE_CONTEXT,production.contextStatus());assertTrue(production.entryAllowed());
        TradeSignal saved=signals.findTopBySymbolAndIntervalAndCandleOpenTimeLessThanEqualAndGeneratedAtLessThanEqualOrderByCandleOpenTimeDesc("BTCUSDT","1m",evaluatedAt.minusSeconds(60),evaluatedAt).orElseThrow();
        var replay=new ExecutionReplayScope();ReflectionTestUtils.setField(context,"replayScope",replay);
        try(var scope=replay.open(142,List.of(saved),ignored->{})) {
            replay.reference(evaluatedAt);var simulated=context.evaluate("ETHUSDT","1m",SignalDecision.BUY,true,evaluatedAt);
            assertEquals(production.contextStatus(),simulated.contextStatus());assertEquals(production.entryAllowed(),simulated.entryAllowed());
        }
        System.out.println("FIX142_BACKLOG_FIXTURE rows="+backlog+" freshPathMs="+elapsed+" budgetMs="+budget+" scoringAndWalletMocked=true");
    }
}
