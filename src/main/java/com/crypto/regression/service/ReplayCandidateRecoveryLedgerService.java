package com.crypto.regression.service;

import com.crypto.regression.replay.ReplayAccessPolicy;
import com.crypto.regression.replay.ReplayCandidateRecoveryPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replay-only research ledger for testing a possible late-entry recovery rule.
 * It deliberately never calls paper-position, wallet, order, or Production execution services.
 */
@Service
@RequiredArgsConstructor
public class ReplayCandidateRecoveryLedgerService {
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public Map<String, Object> rebuild(long runId) {
        ReplayAccessPolicy.require("analysis_test_run", ReplayAccessPolicy.Operation.REPLAY_READ);
        ReplayAccessPolicy.require("trade_signal_test", ReplayAccessPolicy.Operation.REPLAY_READ);
        ReplayAccessPolicy.require("replay_candidate_recovery_ledger", ReplayAccessPolicy.Operation.REPLAY_DELETE);
        ReplayAccessPolicy.require("replay_candidate_recovery_ledger", ReplayAccessPolicy.Operation.REPLAY_INSERT);
        List<Map<String, Object>> runRows = jdbcTemplate.queryForList("SELECT id,symbol,start_time,end_time,status FROM analysis_test_run WHERE id=?", runId);
        if (runRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Replay run not found.");
        Map<String, Object> run = runRows.get(0);
        String status = String.valueOf(run.get("status"));
        if (!"PASSED".equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Candidate recovery requires a PASSED Replay run; current status=" + status + ".");
        if (run.get("start_time") == null || run.get("end_time") == null) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Candidate recovery requires a Replay run with start and end timestamps.");
        jdbcTemplate.update("DELETE FROM replay_candidate_recovery_ledger WHERE test_run_id=?", runId);
        List<Map<String, Object>> signals = jdbcTemplate.queryForList("""
                SELECT id, generated_at, latest_price, original_decision, decision, total_score,
                       raw_confidence_score, final_entry_allowed, primary_blocking_stage,
                       stop_loss, take_profit, atr_retracement_entry_price
                FROM trade_signal_test WHERE test_run_id=? AND interval_code='1m'
                ORDER BY generated_at ASC, id ASC
                """, runId);
        List<Candidate> candidates = new ArrayList<>();
        int sequence = 1;
        BigDecimal cumulative = BigDecimal.ZERO;
        Instant end = ((Timestamp) run.get("end_time")).toInstant();
        Instant virtualPositionUntil = null;
        for (Map<String, Object> signal : signals) {
            Instant at = ((Timestamp) signal.get("generated_at")).toInstant();
            BigDecimal price = decimal(signal.get("latest_price"));
            if (price == null || price.signum() <= 0) continue;
            for (Candidate c : candidates) {
                if (!c.terminal && at.isAfter(c.created.plusSeconds(3600))) {
                    sequence = insert(runId, sequence, at, "EXPIRED", c, null, price, null, null, null, null, null, cumulative,
                            "No fresh eligible BUY arrived within the one-hour candidate window.");
                    c.terminal = true;
                }
            }
            String original = text(signal.get("original_decision"));
            String blocker = text(signal.get("primary_blocking_stage"));
            Integer raw = integer(signal.get("raw_confidence_score"));
            int score = integer(signal.get("total_score")) == null ? 0 : integer(signal.get("total_score"));
            if (isBuy(original) && !truthy(signal.get("final_entry_allowed"))
                    && ReplayCandidateRecoveryPolicy.qualifies(score, raw, blocker)) {
                // A setup is one symbol/blocker consecutive-minute sequence. Retain its first
                // blocked minute and start its 60-minute lifetime there, never at a later repeat.
                boolean sameOpenSetup = candidates.stream().anyMatch(c -> !c.terminal && c.blocker.equals(blocker));
                if (virtualPositionUntil == null || !at.isBefore(virtualPositionUntil)) if (!sameOpenSetup) candidates.add(new Candidate(number(signal.get("id")), at, price, blocker,
                        decimal(signal.get("stop_loss")), decimal(signal.get("take_profit")),
                        decimal(signal.get("atr_retracement_entry_price"))));
                continue;
            }
            if (!isBuy(text(signal.get("decision"))) || !truthy(signal.get("final_entry_allowed"))) continue;
            Candidate c = candidates.stream().filter(candidate -> !candidate.terminal)
                    .filter(candidate -> price.compareTo(ReplayCandidateRecoveryPolicy.releaseCeiling(candidate.price, candidate.retracementPrice, candidate.blocker)) <= 0)
                    .findFirst().orElse(null); // oldest candidate wins deterministically.
            if (c != null) {
                BigDecimal ceiling = ReplayCandidateRecoveryPolicy.releaseCeiling(c.price, c.retracementPrice, c.blocker);
                BigDecimal entry = ReplayCandidateRecoveryPolicy.adverseBuyFill(price);
                if (c.stop == null || c.target == null || c.stop.compareTo(entry) >= 0 || entry.compareTo(c.target) >= 0) {
                    sequence = insert(runId, sequence, at, "BUY_REJECTED_LEVELS", c, number(signal.get("id")), price, null, null, null, null, null, cumulative,
                            "Rejected because required levels do not satisfy stop < adverse entry fill < target.");
                    c.terminal = true;
                    continue;
                }
                sequence = insert(runId, sequence, at, "BUY_ALLOWED", c, number(signal.get("id")), price, entry, null,
                        ReplayCandidateRecoveryPolicy.VIRTUAL_NOTIONAL_USDT, null, null, cumulative,
                        "Replay-only candidate recovery: fresh 1m BUY arrived inside the anti-chase ceiling.");
                Exit exit = findExit(runId, c, at, end, entry);
                if (exit == null) {
                    BigDecimal mark = lastClosedPrice(runId, end);
                    ReplayCandidateRecoveryPolicy.Settlement markSettlement = ReplayCandidateRecoveryPolicy.settle(entry,
                            ReplayCandidateRecoveryPolicy.adverseSellFill(mark));
                    sequence = insert(runId, sequence, end, "OPEN_AT_T2", c, number(signal.get("id")), mark, entry,
                            ReplayCandidateRecoveryPolicy.adverseSellFill(mark), ReplayCandidateRecoveryPolicy.VIRTUAL_NOTIONAL_USDT,
                            markSettlement.feesUsdt(), null, cumulative,
                            "Virtual position remains open at the Replay window end; displayed P&L is unrealized.",
                            markSettlement.realizedPnlUsdt(), markSettlement.realizedPnlPercent());
                } else {
                    ReplayCandidateRecoveryPolicy.Settlement settlement = ReplayCandidateRecoveryPolicy.settle(entry, exit.fill);
                    cumulative = cumulative.add(settlement.realizedPnlUsdt());
                    sequence = insert(runId, sequence, exit.time, "SELL_ALLOWED", c, number(signal.get("id")), exit.decisionPrice, entry, exit.fill,
                            ReplayCandidateRecoveryPolicy.VIRTUAL_NOTIONAL_USDT, settlement.feesUsdt(), settlement, cumulative, exit.reason);
                }
                c.terminal = true;
                virtualPositionUntil = exit == null ? end : exit.time;
                for (Candidate other : candidates) if (!other.terminal) {
                    sequence = insert(runId, sequence, at, "SUPERSEDED", other, number(signal.get("id")), price, null, null, null, null, null, cumulative,
                            "Superseded by the oldest valid candidate released at this fresh BUY.");
                    other.terminal = true;
                }
            } else {
                // Preserve one anti-chase evidence row per candidate, rather than repeating it
                // on every later fresh BUY.
                for (Candidate rejected : candidates) if (!rejected.terminal && !rejected.rejected) {
                    BigDecimal ceiling = ReplayCandidateRecoveryPolicy.releaseCeiling(rejected.price, rejected.retracementPrice, rejected.blocker);
                    if (price.compareTo(ceiling) > 0) {
                        sequence = insert(runId, sequence, at, "BUY_REJECTED", rejected, number(signal.get("id")), price, null, null, null, null, null, cumulative,
                                "Fresh BUY was rejected by the anti-chase ceiling " + ceiling.toPlainString() + ".");
                        rejected.rejected = true;
                    }
                }
            }
        }
        for (Candidate c : candidates) if (!c.terminal) {
            sequence = insert(runId, sequence, end, "EXPIRED", c, null, c.price, null, null, null, null, null, cumulative,
                    "Replay window ended before a fresh eligible BUY released this candidate.");
        }
        return ledger(runId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> ledger(long runId) {
        ReplayAccessPolicy.require("replay_candidate_recovery_ledger", ReplayAccessPolicy.Operation.REPLAY_READ);
        ReplayAccessPolicy.require("replay_position", ReplayAccessPolicy.Operation.REPLAY_READ);
        List<Map<String, Object>> events = jdbcTemplate.queryForList("SELECT * FROM replay_candidate_recovery_ledger WHERE test_run_id=? ORDER BY sequence_no", runId);
        Map<String, Object> summary = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS event_count,
                       SUM(event_type='BUY_ALLOWED') AS buys_allowed,
                       SUM(event_type='SELL_ALLOWED') AS sells_allowed,
                       SUM(event_type='BUY_REJECTED') AS buys_rejected,
                       SUM(event_type='EXPIRED') AS expired,
                       COALESCE(SUM(realized_pnl_usdt),0) AS realized_pnl_usdt,
                       COALESCE(SUM(unrealized_pnl_usdt),0) AS unrealized_pnl_usdt
                FROM replay_candidate_recovery_ledger WHERE test_run_id=?
                """, runId);
        Map<String, Object> baseline = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS positions, SUM(exit_time IS NOT NULL) AS closed_positions,
                       COALESCE(SUM(realized_pnl_usdt),0) AS realized_pnl_usdt
                FROM replay_position WHERE test_run_id=?
                """, runId);
        return Map.of("runId", runId, "events", events, "summary", summary, "baseline", baseline,
                "mode", "REPLAY_ONLY", "productionMutation", false, "paperPositionMutation", false);
    }

    private Exit findExit(long runId, Candidate c, Instant after, Instant end, BigDecimal entry) {
        ReplayAccessPolicy.require("candle", ReplayAccessPolicy.Operation.MARKET_EVIDENCE_READ);
        if (c.stop == null || c.target == null || c.stop.signum() <= 0 || c.target.signum() <= 0) return null;
        List<Map<String, Object>> candles = jdbcTemplate.queryForList("""
                SELECT open_time, open_price, high_price, low_price FROM candle
                WHERE symbol=(SELECT symbol FROM analysis_test_run WHERE id=?) AND interval_code='1m' AND closed=1
                  AND open_time >= ? AND open_time <= ? ORDER BY open_time ASC
                """, runId, Timestamp.from(after), Timestamp.from(end));
        for (Map<String, Object> candle : candles) {
            BigDecimal open = decimal(candle.get("open_price")); BigDecimal high = decimal(candle.get("high_price")); BigDecimal low = decimal(candle.get("low_price"));
            boolean hitStop = low != null && low.compareTo(c.stop) <= 0;
            boolean hitTarget = high != null && high.compareTo(c.target) >= 0;
            if (!hitStop && !hitTarget) continue;
            boolean useStop = hitStop; // same-candle dual hit is intentionally conservative.
            BigDecimal decision = useStop ? (open != null && open.compareTo(c.stop) < 0 ? open : c.stop) : c.target;
            String reason = hitStop && hitTarget ? "AMBIGUOUS_SAME_CANDLE_CONSERVATIVE_STOP" : (useStop ? "VIRTUAL_STOP_LOSS" : "VIRTUAL_TAKE_PROFIT");
            return new Exit(((Timestamp) candle.get("open_time")).toInstant(), decision,
                    ReplayCandidateRecoveryPolicy.adverseSellFill(decision), reason);
        }
        return null;
    }

    private BigDecimal lastClosedPrice(long runId, Instant end) {
        ReplayAccessPolicy.require("candle", ReplayAccessPolicy.Operation.MARKET_EVIDENCE_READ);
        List<BigDecimal> closes = jdbcTemplate.query("""
                SELECT close_price FROM candle WHERE symbol=(SELECT symbol FROM analysis_test_run WHERE id=?)
                  AND interval_code='1m' AND closed=1 AND open_time <= ? ORDER BY open_time DESC LIMIT 1
                """, (rs, row) -> rs.getBigDecimal(1), runId, Timestamp.from(end));
        if (closes.isEmpty() || closes.get(0) == null) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No closed 1m candle is available at or before the Replay end time for mark-to-market.");
        return closes.get(0);
    }

    private int insert(long runId, int sequence, Instant time, String type, Candidate c, Long releaseSignal,
                       BigDecimal decision, BigDecimal entry, BigDecimal exit, BigDecimal notional, BigDecimal fees,
                       ReplayCandidateRecoveryPolicy.Settlement settlement, BigDecimal cumulative, String reason) {
        return insert(runId, sequence, time, type, c, releaseSignal, decision, entry, exit, notional, fees, settlement, cumulative, reason, null, null);
    }
    private int insert(long runId, int sequence, Instant time, String type, Candidate c, Long releaseSignal,
                       BigDecimal decision, BigDecimal entry, BigDecimal exit, BigDecimal notional, BigDecimal fees,
                       ReplayCandidateRecoveryPolicy.Settlement settlement, BigDecimal cumulative, String reason,
                       BigDecimal unrealized, BigDecimal unrealizedPct) {
        jdbcTemplate.update("""
                INSERT INTO replay_candidate_recovery_ledger
                (test_run_id,sequence_no,symbol,event_time,event_type,candidate_signal_id,release_signal_id,blocker,candidate_price,decision_price,virtual_entry_price,virtual_exit_price,virtual_notional_usdt,fees_usdt,slippage_percent,realized_pnl_usdt,realized_pnl_percent,unrealized_pnl_usdt,unrealized_pnl_percent,cumulative_pnl_usdt,reason)
                SELECT ?,?,symbol,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,? FROM analysis_test_run WHERE id=?
                """,
                runId, sequence, Timestamp.from(time), type, c.signalId, releaseSignal, c.blocker, c.price, decision, entry, exit, notional, fees,
                ReplayCandidateRecoveryPolicy.SLIPPAGE_RATE.multiply(new BigDecimal("100")), settlement == null ? null : settlement.realizedPnlUsdt(), settlement == null ? null : settlement.realizedPnlPercent(), unrealized, unrealizedPct, cumulative, reason, runId);
        return sequence + 1;
    }
    private static boolean isBuy(String value) { return "BUY".equals(value) || "STRONG_BUY".equals(value); }
    private static boolean truthy(Object value) { return value instanceof Boolean b ? b : value != null && ("1".equals(value.toString()) || "true".equalsIgnoreCase(value.toString())); }
    private static String text(Object value) { return value == null ? "" : value.toString().trim().toUpperCase(); }
    private static Integer integer(Object value) { return value instanceof Number n ? n.intValue() : value == null ? null : Integer.valueOf(value.toString()); }
    private static Long number(Object value) { return value instanceof Number n ? n.longValue() : value == null ? null : Long.valueOf(value.toString()); }
    private static BigDecimal decimal(Object value) { return value instanceof BigDecimal d ? d : value == null ? null : new BigDecimal(value.toString()); }
    private static final class Candidate { final Long signalId; final Instant created; final BigDecimal price; final String blocker; final BigDecimal stop; final BigDecimal target; final BigDecimal retracementPrice; boolean terminal; boolean rejected; Candidate(Long id, Instant at, BigDecimal p, String b, BigDecimal s, BigDecimal t, BigDecimal r){signalId=id;created=at;price=p;blocker=b;stop=s;target=t;retracementPrice=r;} }
    private record Exit(Instant time, BigDecimal decisionPrice, BigDecimal fill, String reason) { }
}
