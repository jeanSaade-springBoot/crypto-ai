package com.crypto.inspector.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * FIX-121: read-only historical investment reporting. No wallet or Replay mutation.
 * A position is a flat-to-flat execution sequence, including partial SELLs and adds.
 * Never pair one convenient BUY with a terminal SELL or infer cost from realized P/L.
 * Instances are request-local: cached ledgers must never leak between requests/runs.
 */
public final class TradeInvestmentHistory {
    private static final Logger LOG = LoggerFactory.getLogger(TradeInvestmentHistory.class);
    private final JdbcTemplate jdbc;
    private final Map<String, List<Leg>> ledgers = new HashMap<>();
    private final Map<List<Leg>, List<Position>> resolved = new IdentityHashMap<>();

    public TradeInvestmentHistory(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Decimal strings preserve all backend precision through JSON and browser rendering. */
    public record Investment(String entryQuantity, String unitEntryPriceUsdt, String totalInvestedUsdt,
                             int buyCount, String status, String explanation) {
        public static Investment unavailable(String reason) {
            return new Investment(null, null, null, 0, "UNAVAILABLE", reason);
        }
    }

    public record Leg(long id, String symbol, String side, Instant time, BigDecimal quantity,
                      BigDecimal price, BigDecimal amount, Long signalId) {}
    public record Position(List<Leg> legs, BigDecimal remaining, Investment investment) {
        Leg first() { return legs.get(0); }
        Leg last() { return legs.get(legs.size() - 1); }
    }

    public Investment closedWallet(String symbol, long sellId) {
        return report("WALLET", String.valueOf(sellId), unique(partition(wallet(symbol)).stream()
                .filter(p -> p.last().id() == sellId && "SELL".equals(p.last().side()) && p.remaining().signum() == 0).toList()));
    }

    public Investment openWallet(String symbol, Long entrySignalId, BigDecimal remaining) {
        List<Position> matches = partition(wallet(symbol)).stream()
                .filter(p -> p.remaining().signum() > 0 && entrySignalId != null
                        && entrySignalId.equals(p.first().signalId()))
                .filter(p -> remaining != null && closeEnough(p.remaining(), remaining, p.legs().size()))
                .toList();
        return report("WALLET_OPEN", String.valueOf(entrySignalId), unique(matches));
    }

    public List<Leg> closedWalletLegs(String symbol, long sellId) {
        return partition(wallet(symbol)).stream().filter(p -> p.last().id() == sellId
                && p.remaining().signum() == 0).findFirst().map(Position::legs).orElse(List.of());
    }

    private List<Position> partition(List<Leg> legs) {
        return resolved.computeIfAbsent(legs, TradeInvestmentHistory::positions);
    }

    private List<Leg> wallet(String symbol) {
        if (symbol == null) return List.of();
        return ledgers.computeIfAbsent("W:" + symbol, key -> read("""
                SELECT id, symbol, side, executed_at AS execution_time, quantity,
                       price_usdt AS execution_price, gross_amount_usdt AS notional_usdt, signal_id
                FROM wallet_trade WHERE symbol=? AND status='EXECUTED'
                  AND side IN ('BUY','SELL') ORDER BY executed_at,id
                """, symbol));
    }

    /** Run/archive identity is part of the lookup; same-symbol executions in other runs cannot enter. */
    public void enrichReplay(List<Map<String, Object>> trades, Long archiveBatchId) {
        for (Map<String, Object> trade : trades) trade.put("investment", replay(trade, archiveBatchId));
    }

    public Investment replay(Map<String, Object> trade, Long archiveBatchId) {
        Object runId = trade.get("test_run_id");
        String symbol = Objects.toString(trade.get("symbol"), "");
        if (runId == null || symbol.isEmpty()) return Investment.unavailable("Replay source identity is missing.");
        String key = "R:" + archiveBatchId + ":" + runId + ":" + symbol;
        List<Leg> legs = ledgers.computeIfAbsent(key, ignored -> archiveBatchId == null
                ? read("SELECT id,symbol,side,execution_time,quantity,execution_price,notional_usdt FROM wallet_execution_test WHERE test_run_id=? AND symbol=? ORDER BY execution_time,id", runId, symbol)
                : read("SELECT id,symbol,side,execution_time,quantity,execution_price,notional_usdt FROM wallet_execution_test_archive WHERE archive_batch_id=? AND test_run_id=? AND symbol=? ORDER BY execution_time,id", archiveBatchId, runId, symbol));
        Instant entry = instant(trade.get("entry_time")), exit = instant(trade.get("exit_time"));
        List<Position> matches = partition(legs).stream()
                .filter(p -> Objects.equals(p.first().time(), entry))
                .filter(p -> exit == null ? p.remaining().signum() > 0
                        : p.remaining().signum() == 0 && Objects.equals(p.last().time(), exit))
                .toList();
        return report("REPLAY", runId + ":" + trade.get("id"), unique(matches));
    }

    /** Proven records keep their original source; archives and saved execution points are fallback evidence. */
    public void enrichProven(List<Map<String, Object>> trades) {
        for (Map<String, Object> trade : trades) {
            Investment result;
            if (trade.get("source_wallet_sell_trade_id") instanceof Number sell) {
                result = closedWallet(Objects.toString(trade.get("symbol"), ""), sell.longValue());
                if (!"AVAILABLE".equals(result.status())) {
                    List<Leg> saved = read("""
                            SELECT wallet_trade_id AS id, ? AS symbol,side,execution_time,quantity,execution_price
                            FROM proven_trade_execution_point WHERE proven_trade_id=? ORDER BY sequence_no,execution_time
                            """, trade.get("symbol"), trade.get("id"));
                    result = report("PROVEN_POINTS", String.valueOf(trade.get("id")), closed(saved, sell.longValue()));
                }
            } else if (trade.get("source_test_run_id") != null && trade.get("source_trade_id") != null) {
                Map<String, Object> source = new HashMap<>(trade);
                source.put("test_run_id", trade.get("source_test_run_id"));
                source.put("id", trade.get("source_trade_id"));
                result = replay(source, null);
                if (!"AVAILABLE".equals(result.status())) {
                    List<Map<String, Object>> archived = jdbc.queryForList("""
                            SELECT archive_batch_id FROM wallet_position_test_archive
                            WHERE test_run_id=? AND id=? ORDER BY archive_batch_id DESC LIMIT 1
                            """, trade.get("source_test_run_id"), trade.get("source_trade_id"));
                    if (!archived.isEmpty()) result = replay(source, ((Number) archived.get(0).get("archive_batch_id")).longValue());
                }
            } else result = Investment.unavailable("Historical execution source is missing.");
            trade.put("investment", result);
        }
    }

    private List<Leg> read(String sql, Object... args) {
        return jdbc.queryForList(sql, args).stream().map(row -> new Leg(
                row.get("id") instanceof Number id ? id.longValue() : -1L,
                Objects.toString(row.get("symbol"), ""), Objects.toString(row.get("side"), ""),
                instant(row.get("execution_time")), decimal(row.get("quantity")),
                decimal(row.get("execution_price")), decimal(row.get("notional_usdt")),
                row.get("signal_id") instanceof Number signal ? signal.longValue() : null)).toList();
    }

    public static Investment closed(List<Leg> legs, long sellId) {
        return unique(positions(legs).stream().filter(p -> p.last().id() == sellId
                && "SELL".equals(p.last().side()) && p.remaining().signum() == 0).toList());
    }

    private static Investment unique(List<Position> matches) {
        return matches.size() == 1 ? matches.get(0).investment()
                : Investment.unavailable("Complete, unambiguous BUY execution history could not be established.");
    }

    /**
     * Walk immutable execution order. Quantity reconciliation proves the lifecycle boundary.
     * An orphan/overdrawn SELL makes that history ambiguous: do not silently reset it and
     * attribute later BUYs to a fabricated position. Only independent symbols are unaffected.
     */
    public static List<Position> positions(List<Leg> input) {
        Map<String, List<Leg>> symbols = new LinkedHashMap<>();
        for (Leg leg : input) symbols.computeIfAbsent(leg.symbol(), k -> new ArrayList<>()).add(leg);
        List<Position> result = new ArrayList<>();
        for (List<Leg> ledger : symbols.values()) {
            ledger.sort(Comparator.comparing(Leg::time, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparingLong(Leg::id));
            List<Leg> current = new ArrayList<>();
            BigDecimal balance = BigDecimal.ZERO;
            Set<Long> ids = new HashSet<>();
            boolean invalid = false;
            for (Leg leg : ledger) {
                if (leg.time() == null || leg.id() < 0 || !ids.add(leg.id()) || leg.quantity() == null
                        || leg.quantity().signum() <= 0 || !("BUY".equals(leg.side()) || "SELL".equals(leg.side()))) {
                    invalid = true; break;
                }
                current.add(leg);
                balance = "BUY".equals(leg.side()) ? balance.add(leg.quantity()) : balance.subtract(leg.quantity());
                if ("SELL".equals(leg.side())) {
                    BigDecimal acquired = current.stream().filter(x -> "BUY".equals(x.side()))
                            .map(Leg::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal storageRounding = new BigDecimal("0.000000000001").multiply(BigDecimal.valueOf(current.size()));
                    BigDecimal tolerance = storageRounding.min(acquired.multiply(new BigDecimal("0.0000000001")));
                    if (balance.abs().compareTo(tolerance) <= 0) balance = BigDecimal.ZERO;
                }
                if (balance.signum() < 0) { invalid = true; break; }
                if (balance.signum() == 0) {
                    result.add(new Position(List.copyOf(current), balance, aggregate(current)));
                    current.clear();
                }
            }
            if (!invalid && !current.isEmpty()) result.add(new Position(List.copyOf(current), balance, aggregate(current)));
        }
        return result;
    }

    private static boolean closeEnough(BigDecimal left, BigDecimal right, int legs) {
        // Only storage rounding at DECIMAL(...,12), never a percentage-sized missing execution.
        BigDecimal tolerance = new BigDecimal("0.000000000001").multiply(BigDecimal.valueOf(legs))
                .min(left.abs().max(right.abs()).multiply(new BigDecimal("0.0000000001")));
        return left.subtract(right).abs().compareTo(tolerance) <= 0;
    }

    private static Investment aggregate(List<Leg> legs) {
        BigDecimal quantity = BigDecimal.ZERO, invested = BigDecimal.ZERO;
        int buys = 0;
        BigDecimal singlePrice = null;
        for (Leg leg : legs) {
            if (!"BUY".equals(leg.side())) continue;
            if (leg.price() == null || leg.price().signum() <= 0 || (leg.amount() != null && leg.amount().signum() <= 0))
                return Investment.unavailable("A BUY execution has no valid acquisition price or cost.");
            quantity = quantity.add(leg.quantity());
            // Persisted gross spend is the authoritative pre-fee BUY cost; do not use SELL cost basis
            // (partial exits reduce that basis). Saved execution points may only retain quantity/price.
            invested = invested.add(leg.amount() == null ? leg.quantity().multiply(leg.price()) : leg.amount());
            singlePrice = leg.price();
            buys++;
        }
        if (buys == 0) return Investment.unavailable("No BUY executions are available.");
        BigDecimal unit = buys == 1 ? singlePrice : invested.divide(quantity, 12, RoundingMode.HALF_UP);
        return new Investment(plain(quantity), plain(unit), plain(invested), buys, "AVAILABLE",
                "Cumulative BUY quantity and gross investment, excluding fees; partial sells do not reduce these totals."
                        + (buys > 1 ? " Unit entry price is weighted across all BUY executions." : ""));
    }

    private Investment report(String source, String identity, Investment value) {
        // FIX-121: aggregate read-time diagnostics only, not per-market-event logging.
        LOG.info("[FIX-121][INVESTMENT] source={} identity={} status={} buys={} quantity={} invested={} reason={}",
                source, identity, value.status(), value.buyCount(), value.entryQuantity(), value.totalInvestedUsdt(), value.explanation());
        return value;
    }
    private static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private static BigDecimal decimal(Object value) { return value == null ? null : new BigDecimal(value.toString()); }
    private static Instant instant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof Timestamp t) return t.toInstant();
        if (value instanceof LocalDateTime t) return t.toInstant(ZoneOffset.UTC);
        return null;
    }
}
