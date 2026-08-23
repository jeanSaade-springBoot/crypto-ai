package com.crypto.inspector.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Read-only Trade Activity feed.
 *
 * This screen deliberately does NOT participate in signal generation or execution. It only
 * projects already-persisted trade_signal, execution_opportunity, wallet_trade and completed
 * wallet_managed_position evidence into a compact operator view.
 */
@Service
public class TradeActivityService {
    private static final Set<Integer> ALLOWED_HOURS = Set.of(1, 2, 4, 24);
    private static final Set<String> ALLOWED_FILTERS = Set.of(
            "BUY", "SELL", "COUPLE", "BLOCKED", "EXECUTED", "WIN", "LOST");
    private final JdbcTemplate jdbc;

    public TradeActivityService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> search(String symbol, int hours, List<String> requestedFilters) {
        if (!ALLOWED_HOURS.contains(hours)) throw new IllegalArgumentException("Time range must be 1, 2, 4 or 24 hours.");
        Set<String> filters = new LinkedHashSet<>();
        if (requestedFilters != null) requestedFilters.stream().filter(Objects::nonNull)
                .map(v -> v.trim().toUpperCase(Locale.ROOT)).filter(ALLOWED_FILTERS::contains).forEach(filters::add);

        String normalizedSymbol = symbol == null || symbol.isBlank() || "ALL".equalsIgnoreCase(symbol)
                ? null : symbol.trim().toUpperCase(Locale.ROOT);
        Instant from = Instant.now().minus(hours, ChronoUnit.HOURS);

        // FIX-058: COUPLE is an explicit completed-position mode, not another additive side.
        // A couple is the opening BUY and the closing SELL of the same CLOSED
        // wallet_managed_position. The outcome comes from the persisted SELL realized P/L.
        // This prevents a nearby unrelated BUY/SELL from being paired merely because timestamps
        // are close. WIN means realized_pnl_usdt > 0; LOST means realized_pnl_usdt < 0.
        if (filters.contains("COUPLE")) {
            boolean includeWin = filters.contains("WIN");
            boolean includeLost = filters.contains("LOST");
            if (!includeWin && !includeLost) {
                throw new IllegalArgumentException("Select WIN, LOST, or both when COUPLE is selected.");
            }
            return searchCompletedCouples(normalizedSymbol, from, includeWin, includeLost);
        }

        // FIX-049/FIX-058: Normal activity mode retains the strict two-dimensional contract:
        // (BUY or SELL) AND (EXECUTED or BLOCKED) AND symbol. WIN/LOST are couple-only filters.
        List<String> sides = new ArrayList<>();
        if (filters.contains("BUY")) sides.add("BUY");
        if (filters.contains("SELL")) sides.add("SELL");
        boolean includeExecuted = filters.contains("EXECUTED");
        boolean includeBlocked = filters.contains("BLOCKED");
        if (sides.isEmpty()) throw new IllegalArgumentException("Select BUY, SELL, or both.");
        if (!includeExecuted && !includeBlocked) throw new IllegalArgumentException("Select EXECUTED, BLOCKED, or both.");

        List<Map<String, Object>> rows = new ArrayList<>();
        if (includeExecuted) {
            String sideClause = " AND wt.side IN (" + String.join(",", Collections.nCopies(sides.size(), "?")) + ")";
            String sql = """
                    SELECT wt.executed_at event_time, wt.symbol, ts.interval_code timeframe,
                           wt.side action, 'EXECUTED' status,
                           CASE
                             WHEN wt.execution_reason = 'SETUP_CONFIRMATION_WAKEUP' THEN 'WAKE_UP'
                             WHEN wt.execution_reason = 'IMMEDIATE_VALIDATION' THEN 'INITIAL'
                             WHEN wt.execution_reason = 'SCOUT_ENTRY' THEN 'SCOUT'
                             WHEN wt.execution_reason = 'ACCUMULATED_EVIDENCE' THEN 'ACCUMULATED'
                             WHEN wt.side = 'SELL' AND wt.signal_id IS NULL THEN 'POSITION_EXIT'
                             ELSE 'SIGNAL'
                           END source,
                           COALESCE(NULLIF(wt.execution_reason,''), CASE WHEN wt.side='BUY' THEN 'BUY_EXECUTED' ELSE 'SELL_EXECUTED' END) reason
                    FROM wallet_trade wt
                    LEFT JOIN trade_signal ts ON ts.id = wt.signal_id
                    WHERE wt.executed_at >= ? AND wt.status = 'EXECUTED'
                    """ + sideClause + (normalizedSymbol == null ? "" : " AND wt.symbol = ?")
                    + " ORDER BY wt.executed_at DESC LIMIT 500";
            List<Object> args = new ArrayList<>();
            args.add(from);
            args.addAll(sides);
            if (normalizedSymbol != null) args.add(normalizedSymbol);
            rows.addAll(jdbc.queryForList(sql, args.toArray()));
        }

        if (includeBlocked) {
            String sideClause = " AND eo.direction IN (" + String.join(",", Collections.nCopies(sides.size(), "?")) + ")";
            String sql = """
                    SELECT eo.last_evidence_at event_time, eo.symbol, ts.interval_code timeframe,
                           eo.direction action, 'BLOCKED' status, 'EXECUTION_GATE' source,
                           COALESCE(NULLIF(eo.decision_code,''),'BLOCKED') reason
                    FROM execution_opportunity eo
                    LEFT JOIN trade_signal ts ON ts.id = eo.latest_signal_id
                    WHERE eo.last_evidence_at >= ? AND eo.status IN ('BLOCKED','CANCELLED')
                    """ + sideClause + (normalizedSymbol == null ? "" : " AND eo.symbol = ?")
                    + " ORDER BY eo.last_evidence_at DESC LIMIT 500";
            List<Object> args = new ArrayList<>();
            args.add(from);
            args.addAll(sides);
            if (normalizedSymbol != null) args.add(normalizedSymbol);
            rows.addAll(jdbc.queryForList(sql, args.toArray()));
        }

        rows.sort((a,b) -> ((Comparable)b.get("event_time")).compareTo(a.get("event_time")));
        return rows.stream().limit(500).toList();
    }

    /**
     * FIX-058 completed BUY/SELL pair search.
     *
     * Pair authority is wallet_managed_position rather than timestamp guessing:
     * - BUY is resolved from the position's immutable entry_signal_id/opened_at.
     * - SELL is the execution nearest the CLOSED position's updated_at inside that lifecycle.
     * - WIN/LOST is read from the SELL's persisted realized_pnl_usdt.
     * Both rows are returned adjacent in the existing Trade Activity grid (BUY first, SELL second).
     */
    private List<Map<String, Object>> searchCompletedCouples(String normalizedSymbol, Instant from,
                                                              boolean includeWin, boolean includeLost) {
        String outcomeClause;
        if (includeWin && includeLost) outcomeClause = " AND sell.realized_pnl_usdt <> 0";
        else if (includeWin) outcomeClause = " AND sell.realized_pnl_usdt > 0";
        else outcomeClause = " AND sell.realized_pnl_usdt < 0";

        String sql = """
                WITH completed AS (
                    SELECT p.id position_id, p.symbol, p.entry_signal_id, p.opened_at, p.updated_at,
                           (
                             SELECT b.id
                             FROM wallet_trade b
                             WHERE b.symbol = p.symbol
                               AND b.side = 'BUY'
                               AND b.status = 'EXECUTED'
                               AND b.executed_at BETWEEN p.opened_at - INTERVAL 10 SECOND
                                                     AND p.opened_at + INTERVAL 60 SECOND
                             ORDER BY CASE WHEN b.signal_id = p.entry_signal_id THEN 0 ELSE 1 END,
                                      ABS(TIMESTAMPDIFF(MICROSECOND, b.executed_at, p.opened_at))
                             LIMIT 1
                           ) buy_trade_id,
                           (
                             SELECT s.id
                             FROM wallet_trade s
                             WHERE s.symbol = p.symbol
                               AND s.side = 'SELL'
                               AND s.status = 'EXECUTED'
                               AND s.executed_at >= p.opened_at
                               AND s.executed_at <= p.updated_at + INTERVAL 60 SECOND
                             ORDER BY ABS(TIMESTAMPDIFF(MICROSECOND, s.executed_at, p.updated_at))
                             LIMIT 1
                           ) sell_trade_id
                    FROM wallet_managed_position p
                    WHERE p.status = 'CLOSED'
                      AND p.updated_at >= ?
                """ + (normalizedSymbol == null ? "" : " AND p.symbol = ?") + """
                ), pairs AS (
                    SELECT c.*, sell.realized_pnl_usdt, sell.executed_at sell_time
                    FROM completed c
                    JOIN wallet_trade sell ON sell.id = c.sell_trade_id
                    WHERE c.buy_trade_id IS NOT NULL AND c.sell_trade_id IS NOT NULL
                """ + outcomeClause + """
                )
                SELECT wt.executed_at event_time,
                       wt.symbol,
                       COALESCE(ts.interval_code, entry_ts.interval_code) timeframe,
                       wt.side action,
                       CASE WHEN p.realized_pnl_usdt > 0 THEN 'WIN' ELSE 'LOST' END status,
                       'COUPLE' source,
                       COALESCE(NULLIF(wt.execution_reason,''),
                                CASE WHEN wt.side='BUY' THEN 'BUY_EXECUTED' ELSE 'SELL_EXECUTED' END) reason,
                       p.position_id pair_id,
                       p.sell_time pair_time,
                       CASE WHEN wt.side='BUY' THEN 0 ELSE 1 END pair_order
                FROM pairs p
                JOIN wallet_trade wt ON wt.id IN (p.buy_trade_id, p.sell_trade_id)
                LEFT JOIN trade_signal ts ON ts.id = wt.signal_id
                LEFT JOIN trade_signal entry_ts ON entry_ts.id = p.entry_signal_id
                ORDER BY p.sell_time DESC, p.position_id DESC, pair_order ASC
                LIMIT 500
                """;

        List<Object> args = new ArrayList<>();
        args.add(from);
        if (normalizedSymbol != null) args.add(normalizedSymbol);
        return jdbc.queryForList(sql, args.toArray());
    }

    @Transactional(readOnly = true)
    public List<String> symbols() {
        return jdbc.queryForList("""
                SELECT symbol
                FROM (
                    SELECT DISTINCT symbol FROM trade_signal WHERE symbol IS NOT NULL AND symbol <> ''
                    UNION
                    SELECT DISTINCT symbol FROM execution_opportunity WHERE symbol IS NOT NULL AND symbol <> ''
                    UNION
                    SELECT DISTINCT symbol FROM wallet_trade WHERE symbol IS NOT NULL AND symbol <> ''
                ) activity_symbols
                WHERE symbol <> 'USDT'
                ORDER BY symbol
                """, String.class);
    }
}
