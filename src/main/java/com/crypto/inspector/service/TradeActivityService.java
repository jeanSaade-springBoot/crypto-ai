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
 * projects already-persisted trade_signal, execution_opportunity and wallet_trade evidence
 * into a compact operator view. Keeping this separate from Trade Inspector prevents the
 * forensic trade-quality screen from becoming an operational event log.
 */
@Service
public class TradeActivityService {
    private static final Set<Integer> ALLOWED_HOURS = Set.of(1, 2, 4, 24);
    private static final Set<String> ALLOWED_FILTERS = Set.of("BUY", "SELL", "BLOCKED", "EXECUTED");
    private final JdbcTemplate jdbc;

    public TradeActivityService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> search(String symbol, int hours, List<String> requestedFilters) {
        if (!ALLOWED_HOURS.contains(hours)) throw new IllegalArgumentException("Time range must be 1, 2, 4 or 24 hours.");
        Set<String> filters = new LinkedHashSet<>();
        if (requestedFilters != null) requestedFilters.stream().filter(Objects::nonNull)
                .map(v -> v.trim().toUpperCase(Locale.ROOT)).filter(ALLOWED_FILTERS::contains).forEach(filters::add);
        if (filters.isEmpty()) throw new IllegalArgumentException("Select BUY, SELL, BLOCKED or EXECUTED.");

        String normalizedSymbol = symbol == null || symbol.isBlank() || "ALL".equalsIgnoreCase(symbol) ? null : symbol.trim().toUpperCase(Locale.ROOT);
        Instant from = Instant.now().minus(hours, ChronoUnit.HOURS);
        List<Map<String, Object>> rows = new ArrayList<>();

        // FIX-048: Trade Activity filters now follow operator intent instead of acting as
        // independent additive database sources. BUY/SELL are broad signal-side filters;
        // EXECUTED narrows those sides to actual wallet executions, and BLOCKED is exclusive.
        // Database/Binance timestamps remain UTC; only the frontend converts them for display.
        if (filters.contains("BLOCKED")) {
            // BLOCKED is exclusive by design: when requested, return only blocked/cancelled
            // execution opportunities for the selected symbol and time window.
            String sql = """
                    SELECT eo.last_evidence_at event_time, eo.symbol, ts.interval_code timeframe,
                           eo.direction action, 'BLOCKED' status, 'EXECUTION_GATE' source,
                           COALESCE(NULLIF(eo.decision_code,''),'BLOCKED') reason
                    FROM execution_opportunity eo
                    LEFT JOIN trade_signal ts ON ts.id = eo.latest_signal_id
                    WHERE eo.last_evidence_at >= ? AND eo.status IN ('BLOCKED','CANCELLED')
                    """ + (normalizedSymbol == null ? "" : " AND eo.symbol = ?")
                    + " ORDER BY eo.last_evidence_at DESC LIMIT 500";
            rows.addAll(normalizedSymbol == null ? jdbc.queryForList(sql, from) : jdbc.queryForList(sql, from, normalizedSymbol));
        } else if (filters.contains("EXECUTED")) {
            // EXECUTED means a real wallet ledger execution. BUY/SELL, when also selected,
            // narrow the wallet result by side; EXECUTED alone returns both BUY and SELL.
            List<String> executedSides = new ArrayList<>();
            if (filters.contains("BUY")) executedSides.add("BUY");
            if (filters.contains("SELL")) executedSides.add("SELL");

            String sideClause = "";
            if (!executedSides.isEmpty()) {
                sideClause = " AND wt.side IN (" + String.join(",", Collections.nCopies(executedSides.size(), "?")) + ")";
            }

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
            args.addAll(executedSides);
            if (normalizedSymbol != null) args.add(normalizedSymbol);
            rows.addAll(jdbc.queryForList(sql, args.toArray()));
        } else if (filters.contains("BUY") || filters.contains("SELL")) {
            // BUY/SELL without EXECUTED means "show me all signals on this side".
            // Mark a signal EXECUTED only when a matching wallet_trade exists for that signal;
            // otherwise keep it as SIGNAL so the operator can distinguish executed vs not executed.
            List<String> sides = new ArrayList<>();
            if (filters.contains("BUY")) { sides.add("BUY"); sides.add("STRONG_BUY"); }
            if (filters.contains("SELL")) { sides.add("SELL"); sides.add("STRONG_SELL"); }
            String placeholders = String.join(",", Collections.nCopies(sides.size(), "?"));
            String sql = """
                    SELECT ts.generated_at event_time, ts.symbol, ts.interval_code timeframe,
                           CASE WHEN ts.decision IN ('BUY','STRONG_BUY') THEN 'BUY' ELSE 'SELL' END action,
                           CASE WHEN EXISTS (
                               SELECT 1 FROM wallet_trade wt
                               WHERE wt.signal_id = ts.id AND wt.status = 'EXECUTED'
                                 AND wt.side = CASE WHEN ts.decision IN ('BUY','STRONG_BUY') THEN 'BUY' ELSE 'SELL' END
                           ) THEN 'EXECUTED' ELSE 'SIGNAL' END status,
                           'INITIAL_SIGNAL' source,
                           CASE WHEN ts.decision IN ('BUY','STRONG_BUY') THEN 'BUY_SIGNAL' ELSE 'SELL_SIGNAL' END reason
                    FROM trade_signal ts
                    WHERE ts.generated_at >= ? AND ts.decision IN (%s)
                    """.formatted(placeholders) + (normalizedSymbol == null ? "" : " AND ts.symbol = ?")
                    + " ORDER BY ts.generated_at DESC LIMIT 500";
            List<Object> args = new ArrayList<>();
            args.add(from);
            args.addAll(sides);
            if (normalizedSymbol != null) args.add(normalizedSymbol);
            rows.addAll(jdbc.queryForList(sql, args.toArray()));
        }

        rows.sort((a,b) -> ((Comparable)b.get("event_time")).compareTo(a.get("event_time")));
        return rows.stream().limit(500).toList();
    }

    @Transactional(readOnly = true)
    /**
     * FIX-046: Trade Activity symbols must come from the activity evidence itself, not only
     * from wallet_asset. A symbol can have signals or blocked opportunities before it ever
     * becomes a wallet holding, so limiting this dropdown to wallet_asset made valid symbols
     * appear unsearchable. This query is read-only and does not alter trading behavior.
     */
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
