package com.crypto.health.service;

import com.crypto.administration.service.CoinConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FIX-071: read-only daily operational health built from production tables.
 * Trading decisions are never changed here; this service only exposes observability.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemHealthDailyService {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Riyadh");
    private static final int BASELINE_DAYS = 7;

    private final JdbcTemplate jdbc;
    private final CoinConfigurationService coinConfigurationService;

    @Transactional(readOnly = true)
    public Map<String, Object> dailyHealth() {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(DISPLAY_ZONE);
        Instant from = today.atStartOfDay(DISPLAY_ZONE).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(DISPLAY_ZONE).toInstant();
        Instant baselineFrom = from.minus(Duration.ofDays(BASELINE_DAYS));

        // FIX-071C: System Health is an observability endpoint and must never become a blind spot.
        // Each diagnostic section is isolated so one schema/query problem is surfaced as a named
        // CRITICAL health alert while the rest of the page continues to load. Trading is untouched.
        List<Map<String, Object>> diagnosticErrors = new ArrayList<>();

        Map<String, Long> candleCounts = safe("Core candle counts", diagnosticErrors,
                () -> intervalCounts("candle", "open_time", "closed = 1", from, to), new LinkedHashMap<>());
        Map<String, Long> signalCounts = safe("Core signal counts", diagnosticErrors,
                () -> intervalCounts("trade_signal", "generated_at", "1 = 1", from, to), new LinkedHashMap<>());
        long buyCount = safe("BUY count", diagnosticErrors,
                () -> scalarLong("SELECT COUNT(*) FROM wallet_trade WHERE side='BUY' AND executed_at >= ? AND executed_at < ?", from, to), 0L);
        long sellCount = safe("SELL count", diagnosticErrors,
                () -> scalarLong("SELECT COUNT(*) FROM wallet_trade WHERE side='SELL' AND executed_at >= ? AND executed_at < ?", from, to), 0L);
        long openPositions = safe("Open position count", diagnosticErrors,
                () -> scalarLong("SELECT COUNT(*) FROM wallet_managed_position WHERE status='OPEN'"), 0L);
        long missingContext = safe("Missing-context count", diagnosticErrors,
                () -> scalarLong("SELECT COUNT(*) FROM execution_opportunity WHERE started_at >= ? AND started_at < ? AND decision_code='MISSING_CONTEXT'", from, to), 0L);

        Set<String> enabledSymbols = safe("Enabled symbol lookup", diagnosticErrors,
                () -> new LinkedHashSet<>(coinConfigurationService.enabledSymbols()), new LinkedHashSet<>());
        List<Map<String, Object>> signalStaleness = safe("Signal staleness", diagnosticErrors,
                () -> signalStaleness(now, enabledSymbols), new ArrayList<>());
        List<Map<String, Object>> candleStaleness = safe("Candle staleness", diagnosticErrors,
                () -> candleStaleness(now, enabledSymbols), new ArrayList<>());
        List<Map<String, Object>> tradeBaseline = safe("BUY/SELL baseline", diagnosticErrors,
                () -> tradeBaseline(from, to, baselineFrom), new ArrayList<>());
        List<Map<String, Object>> routes = safe("Entry-route distribution", diagnosticErrors,
                () -> routeDistribution(from, to, baselineFrom), new ArrayList<>());
        List<Map<String, Object>> strategyRegimes = safe("Strategy/regime distribution", diagnosticErrors,
                () -> strategyRegimeDistribution(from, to, baselineFrom), new ArrayList<>());
        List<Map<String, Object>> opportunityOutcomes = safe("Opportunity outcome distribution", diagnosticErrors,
                () -> opportunityOutcomeDistribution(from, to, baselineFrom), new ArrayList<>());

        String balanceStatus = buySellBalanceStatus(buyCount, sellCount, openPositions);
        String balanceMessage = buySellBalanceMessage(buyCount, sellCount, openPositions);
        String missingContextStatus = missingContext > 5 ? "CRITICAL" : missingContext > 0 ? "WARNING" : "OK";

        List<Map<String, Object>> alerts = buildAlerts(signalStaleness, candleStaleness, balanceStatus,
                balanceMessage, missingContextStatus, missingContext);
        alerts.addAll(diagnosticErrors);
        alerts.sort((a, b) -> Integer.compare(statusRank((String) a.get("status")), statusRank((String) b.get("status"))));
        String overall = overallStatus(alerts);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("candleCounts", normalizedIntervalCounts(candleCounts));
        summary.put("signalCounts", normalizedIntervalCounts(signalCounts));
        summary.put("buyCount", buyCount);
        summary.put("sellCount", sellCount);
        summary.put("openPositions", openPositions);
        summary.put("buySellStatus", balanceStatus);
        summary.put("buySellMessage", balanceMessage);
        summary.put("missingContextCount", missingContext);
        summary.put("missingContextStatus", missingContextStatus);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", overall);
        response.put("generatedAt", now);
        response.put("displayZone", DISPLAY_ZONE.getId());
        response.put("day", today.toString());
        response.put("dayStartUtc", from);
        response.put("dayEndUtc", to);
        response.put("baselineDays", BASELINE_DAYS);
        response.put("summary", summary);
        response.put("alerts", alerts);
        response.put("signalStaleness", signalStaleness);
        response.put("candleStaleness", candleStaleness);
        response.put("tradeBaseline", tradeBaseline);
        response.put("entryRoutes", routes);
        response.put("strategyRegimes", strategyRegimes);
        response.put("opportunityOutcomes", opportunityOutcomes);
        return response;
    }

    private <T> T safe(String component, List<Map<String, Object>> errors, java.util.function.Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (Exception ex) {
            log.error("FIX-071C System Health component failed: {}", component, ex);
            Throwable root = ex;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            String detail = root.getMessage() != null ? root.getMessage() : ex.getMessage();
            if (detail == null || detail.isBlank()) detail = ex.getClass().getSimpleName();
            // Keep the browser message useful but bounded; full stack trace remains in application logs.
            if (detail.length() > 300) detail = detail.substring(0, 300);
            errors.add(alert("CRITICAL", "Health diagnostic failed: " + component, detail));
            return fallback;
        }
    }

    private Map<String, Long> intervalCounts(String table, String timestampColumn, String extraWhere, Instant from, Instant to) {
        String sql = "SELECT interval_code, COUNT(*) cnt FROM " + table
                + " WHERE " + timestampColumn + " >= ? AND " + timestampColumn + " < ? AND " + extraWhere
                + " GROUP BY interval_code";
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query(sql, ps -> {
            ps.setTimestamp(1, Timestamp.from(from));
            ps.setTimestamp(2, Timestamp.from(to));
        }, rs -> {
            // FIX-071A: use a block lambda so JdbcTemplate resolves RowCallbackHandler unambiguously.
            counts.put(rs.getString("interval_code"), rs.getLong("cnt"));
        });
        return counts;
    }

    private Map<String, Long> normalizedIntervalCounts(Map<String, Long> values) {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("1m", values.getOrDefault("1m", 0L));
        result.put("5m", values.getOrDefault("5m", 0L));
        result.put("1h", values.getOrDefault("1h", 0L));
        return result;
    }

    private List<Map<String, Object>> signalStaleness(Instant now, Set<String> enabledSymbols) {
        String sql = """
                SELECT interval_code, symbol, MAX(generated_at) last_signal
                FROM trade_signal
                WHERE generated_at >= ? AND interval_code IN ('1m','5m','1h')
                GROUP BY interval_code, symbol
                """;
        Map<String, Instant> latest = new LinkedHashMap<>();
        Instant cutoff = now.minus(Duration.ofHours(4));
        jdbc.query(sql, ps -> ps.setTimestamp(1, Timestamp.from(cutoff)), rs -> {
            // FIX-071A: avoid expression lambdas returning Map.put(...) values; they make query(...) ambiguous.
            latest.put(rs.getString("symbol") + "|" + rs.getString("interval_code"),
                    rs.getTimestamp("last_signal").toInstant());
        });
        return stalenessRows(enabledSymbols, latest, now, true);
    }

    private List<Map<String, Object>> candleStaleness(Instant now, Set<String> enabledSymbols) {
        String sql = """
                SELECT interval_code, symbol, MAX(close_time) last_candle
                FROM candle
                WHERE open_time >= ? AND closed = 1 AND interval_code IN ('1m','5m','1h')
                GROUP BY interval_code, symbol
                """;
        Map<String, Instant> latest = new LinkedHashMap<>();
        Instant cutoff = now.minus(Duration.ofHours(4));
        jdbc.query(sql, ps -> ps.setTimestamp(1, Timestamp.from(cutoff)), rs -> {
            // FIX-071A: keep the callback void-returning so RowCallbackHandler is selected explicitly.
            latest.put(rs.getString("symbol") + "|" + rs.getString("interval_code"),
                    rs.getTimestamp("last_candle").toInstant());
        });
        return stalenessRows(enabledSymbols, latest, now, false);
    }

    private List<Map<String, Object>> stalenessRows(Set<String> enabledSymbols,
                                                     Map<String, Instant> latest,
                                                     Instant now,
                                                     boolean signal) {
        Set<String> symbols = enabledSymbols.isEmpty()
                ? latest.keySet().stream().map(key -> key.substring(0, key.indexOf('|')))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                : enabledSymbols;
        List<Map<String, Object>> result = new ArrayList<>();
        for (String symbol : symbols) {
            for (String interval : List.of("1m", "5m", "1h")) {
                Instant last = latest.get(symbol + "|" + interval);
                Long minutes = last == null ? null : Math.max(0, Duration.between(last, now).toMinutes());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("interval", interval);
                row.put("symbol", symbol);
                row.put("lastAt", last);
                row.put("minutesStale", minutes);
                row.put("status", last == null ? "CRITICAL"
                        : signal ? signalStalenessStatus(interval, minutes) : candleStalenessStatus(interval, minutes));
                result.add(row);
            }
        }
        result.sort(this::compareHealthRows);
        return result;
    }

    private List<Map<String, Object>> tradeBaseline(Instant from, Instant to, Instant baselineFrom) {
        boolean baselineReady = hasFullHistory("wallet_trade", "executed_at", baselineFrom);
        Map<String, Long> today = keyedCounts(
                "SELECT side k, COUNT(*) cnt FROM wallet_trade WHERE executed_at >= ? AND executed_at < ? GROUP BY side",
                from, to);
        Map<String, BigDecimal> baseline = baselineDailyAverages(
                "SELECT side k, DATE(DATE_ADD(executed_at, INTERVAL 3 HOUR)) d, COUNT(*) cnt FROM wallet_trade WHERE executed_at >= ? AND executed_at < ? GROUP BY side, DATE(DATE_ADD(executed_at, INTERVAL 3 HOUR))",
                baselineFrom, from);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (String side : List.of("BUY", "SELL")) {
            long count = today.getOrDefault(side, 0L);
            BigDecimal avg = baseline.get(side);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("side", side);
            row.put("todayCount", count);
            row.put("baselineAvg", avg);
            row.put("status", baselineLowStatus(count, avg, baselineReady));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> routeDistribution(Instant from, Instant to, Instant baselineFrom) {
        boolean baselineReady = hasFullHistory("wallet_trade", "executed_at", baselineFrom);
        Map<String, Long> today = keyedCounts(
                "SELECT COALESCE(execution_reason,'UNKNOWN') k, COUNT(*) cnt FROM wallet_trade WHERE side='BUY' AND executed_at >= ? AND executed_at < ? GROUP BY COALESCE(execution_reason,'UNKNOWN')",
                from, to);
        Map<String, BigDecimal> baseline = baselineDailyAverages(
                "SELECT COALESCE(execution_reason,'UNKNOWN') k, DATE(DATE_ADD(executed_at, INTERVAL 3 HOUR)) d, COUNT(*) cnt FROM wallet_trade WHERE side='BUY' AND executed_at >= ? AND executed_at < ? GROUP BY COALESCE(execution_reason,'UNKNOWN'), DATE(DATE_ADD(executed_at, INTERVAL 3 HOUR))",
                baselineFrom, from);

        Set<String> routes = new LinkedHashSet<>();
        routes.addAll(today.keySet());
        routes.addAll(baseline.keySet());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String route : routes) {
            long count = today.getOrDefault(route, 0L);
            BigDecimal avg = baseline.get(route);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("route", route);
            row.put("todayCount", count);
            row.put("baselineAvg", avg);
            row.put("status", !baselineReady ? "LEARNING" : count == 0 && avg != null && avg.signum() > 0 ? "WARNING" : "OK");
            rows.add(row);
        }
        rows.sort((a, b) -> Long.compare((Long) b.get("todayCount"), (Long) a.get("todayCount")));
        return rows;
    }

    private List<Map<String, Object>> strategyRegimeDistribution(Instant from, Instant to, Instant baselineFrom) {
        boolean baselineReady = hasFullHistory("trade_signal", "generated_at", baselineFrom);
        Map<String, Long> today = keyedCounts(
                "SELECT CONCAT(selected_strategy,'|',market_regime) k, COUNT(*) cnt FROM trade_signal WHERE generated_at >= ? AND generated_at < ? GROUP BY selected_strategy, market_regime",
                from, to);
        Map<String, BigDecimal> baseline = baselineDailyAverages(
                "SELECT CONCAT(selected_strategy,'|',market_regime) k, DATE(DATE_ADD(generated_at, INTERVAL 3 HOUR)) d, COUNT(*) cnt FROM trade_signal WHERE generated_at >= ? AND generated_at < ? GROUP BY selected_strategy, market_regime, DATE(DATE_ADD(generated_at, INTERVAL 3 HOUR))",
                baselineFrom, from);

        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(today.keySet());
        keys.addAll(baseline.keySet());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String key : keys) {
            String[] parts = key.split("\\|", 2);
            long count = today.getOrDefault(key, 0L);
            BigDecimal avg = baseline.get(key);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("strategy", parts.length > 0 ? parts[0] : "UNKNOWN");
            row.put("regime", parts.length > 1 ? parts[1] : "UNKNOWN");
            row.put("todayCount", count);
            row.put("baselineAvg", avg);
            row.put("status", baselineLowStatus(count, avg, baselineReady));
            rows.add(row);
        }
        rows.sort((a, b) -> {
            int statusCompare = Integer.compare(statusRank((String) a.get("status")), statusRank((String) b.get("status")));
            return statusCompare != 0 ? statusCompare : Long.compare((Long) b.get("todayCount"), (Long) a.get("todayCount"));
        });
        return rows;
    }

    private List<Map<String, Object>> opportunityOutcomeDistribution(Instant from, Instant to, Instant baselineFrom) {
        Map<String, Long> today = keyedCounts(
                "SELECT CONCAT(x.status,'|',COALESCE(x.decision_code,'NULL')) k, x.cnt FROM (" +
                        "SELECT status, decision_code, COUNT(*) cnt FROM execution_opportunity " +
                        "WHERE started_at >= ? AND started_at < ? GROUP BY status, decision_code" +
                        ") x",
                from, to);
        Map<String, BigDecimal> baseline = baselineDailyAverages(
                "SELECT CONCAT(x.status,'|',COALESCE(x.decision_code,'NULL')) k, x.d, x.cnt FROM (" +
                        "SELECT status, decision_code, DATE(DATE_ADD(started_at, INTERVAL 3 HOUR)) d, COUNT(*) cnt " +
                        "FROM execution_opportunity WHERE started_at >= ? AND started_at < ? " +
                        "GROUP BY status, decision_code, DATE(DATE_ADD(started_at, INTERVAL 3 HOUR))" +
                        ") x",
                baselineFrom, from);

        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(today.keySet());
        keys.addAll(baseline.keySet());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String key : keys) {
            String[] parts = key.split("\\|", 2);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("opportunityStatus", parts.length > 0 ? parts[0] : "UNKNOWN");
            row.put("decisionCode", parts.length > 1 ? parts[1] : "NULL");
            row.put("todayCount", today.getOrDefault(key, 0L));
            row.put("baselineAvg", baseline.get(key));
            rows.add(row);
        }
        rows.sort((a, b) -> Long.compare((Long) b.get("todayCount"), (Long) a.get("todayCount")));
        return rows;
    }

    private Map<String, Long> keyedCounts(String sql, Instant from, Instant to) {
        Map<String, Long> values = new LinkedHashMap<>();
        jdbc.query(sql, ps -> {
            ps.setTimestamp(1, Timestamp.from(from));
            ps.setTimestamp(2, Timestamp.from(to));
        }, rs -> {
            // FIX-071A: block lambda prevents Map.put return value from matching ResultSetExtractor<T>.
            values.put(rs.getString("k"), rs.getLong("cnt"));
        });
        return values;
    }

    private Map<String, BigDecimal> baselineDailyAverages(String sql, Instant from, Instant to) {
        Map<String, List<Long>> daily = new LinkedHashMap<>();
        jdbc.query(sql, ps -> {
            ps.setTimestamp(1, Timestamp.from(from));
            ps.setTimestamp(2, Timestamp.from(to));
        }, rs -> {
            // FIX-071A: List.add returns boolean, so an expression lambda is ambiguous with ResultSetExtractor<Boolean>.
            daily.computeIfAbsent(rs.getString("k"), ignored -> new ArrayList<>()).add(rs.getLong("cnt"));
        });
        Map<String, BigDecimal> averages = new LinkedHashMap<>();
        daily.forEach((key, counts) -> {
            if (counts.isEmpty()) return;
            long total = counts.stream().mapToLong(Long::longValue).sum();
            averages.put(key, BigDecimal.valueOf(total)
                    .divide(BigDecimal.valueOf(counts.size()), 1, RoundingMode.HALF_UP));
        });
        return averages;
    }

    private boolean hasFullHistory(String table, String column, Instant requiredStart) {
        Timestamp oldest = jdbc.queryForObject("SELECT MIN(" + column + ") FROM " + table, Timestamp.class);
        return oldest != null && !oldest.toInstant().isAfter(requiredStart);
    }

    private long scalarLong(String sql, Instant... params) {
        Long value = jdbc.queryForObject(sql, Long.class,
                java.util.Arrays.stream(params).map(Timestamp::from).toArray());
        return value == null ? 0L : value;
    }

    private String baselineLowStatus(long today, BigDecimal baseline, boolean ready) {
        if (!ready || baseline == null) return "LEARNING";
        BigDecimal current = BigDecimal.valueOf(today);
        if (current.compareTo(baseline.multiply(BigDecimal.valueOf(0.30))) < 0) return "CRITICAL";
        if (current.compareTo(baseline.multiply(BigDecimal.valueOf(0.60))) < 0) return "WARNING";
        return "OK";
    }

    private String signalStalenessStatus(String interval, long minutes) {
        return switch (interval) {
            case "1m" -> minutes > 5 ? "CRITICAL" : minutes > 2 ? "WARNING" : "OK";
            case "5m" -> minutes > 15 ? "CRITICAL" : minutes > 7 ? "WARNING" : "OK";
            case "1h" -> minutes > 100 ? "CRITICAL" : minutes > 70 ? "WARNING" : "OK";
            default -> "OK";
        };
    }

    private String candleStalenessStatus(String interval, long minutes) {
        return switch (interval) {
            case "1m" -> minutes > 3 ? "CRITICAL" : minutes > 1 ? "WARNING" : "OK";
            case "5m" -> minutes > 15 ? "CRITICAL" : minutes > 7 ? "WARNING" : "OK";
            case "1h" -> minutes > 100 ? "CRITICAL" : minutes > 70 ? "WARNING" : "OK";
            default -> "OK";
        };
    }

    private String buySellBalanceStatus(long buys, long sells, long openPositions) {
        // Progressive entries can legitimately create more BUY rows than SELL rows. The alert is therefore
        // diagnostic rather than a hard trading failure and only fires on a material imbalance.
        if (buys >= 5 && sells == 0 && openPositions == 0) return "CRITICAL";
        if (buys >= 5 && (sells == 0 || buys >= sells * 3L)) return "WARNING";
        return "OK";
    }

    private String buySellBalanceMessage(long buys, long sells, long openPositions) {
        if ("CRITICAL".equals(buySellBalanceStatus(buys, sells, openPositions))) {
            return "BUY activity has no matching SELL activity and there are no open positions; inspect the exit pipeline.";
        }
        if ("WARNING".equals(buySellBalanceStatus(buys, sells, openPositions))) {
            return "BUY activity materially exceeds SELL activity. Check open positions and exit processing before treating this as a fault.";
        }
        return "BUY/SELL activity is not showing a material execution imbalance.";
    }

    private List<Map<String, Object>> buildAlerts(List<Map<String, Object>> signalRows,
                                                   List<Map<String, Object>> candleRows,
                                                   String balanceStatus,
                                                   String balanceMessage,
                                                   String missingContextStatus,
                                                   long missingContext) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        signalRows.stream().filter(row -> !"OK".equals(row.get("status"))).limit(8)
                .forEach(row -> alerts.add(alert((String) row.get("status"), "Signal staleness",
                        row.get("minutesStale") == null
                                ? row.get("symbol") + " " + row.get("interval") + " has no signal history."
                                : row.get("symbol") + " " + row.get("interval") + " signal is " + row.get("minutesStale") + " min stale.")));
        candleRows.stream().filter(row -> !"OK".equals(row.get("status"))).limit(8)
                .forEach(row -> alerts.add(alert((String) row.get("status"), "Candle staleness",
                        row.get("minutesStale") == null
                                ? row.get("symbol") + " " + row.get("interval") + " has no closed candle history."
                                : row.get("symbol") + " " + row.get("interval") + " candle is " + row.get("minutesStale") + " min stale.")));
        if (!"OK".equals(balanceStatus)) alerts.add(alert(balanceStatus, "BUY/SELL balance", balanceMessage));
        if (!"OK".equals(missingContextStatus)) alerts.add(alert(missingContextStatus, "Missing context",
                missingContext + " opportunities reported MISSING_CONTEXT today."));
        alerts.sort((a, b) -> Integer.compare(statusRank((String) a.get("status")), statusRank((String) b.get("status"))));
        return alerts;
    }

    private Map<String, Object> alert(String status, String title, String message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", status);
        row.put("title", title);
        row.put("message", message);
        return row;
    }

    private String overallStatus(List<Map<String, Object>> alerts) {
        if (alerts.stream().anyMatch(row -> "CRITICAL".equals(row.get("status")))) return "CRITICAL";
        if (alerts.stream().anyMatch(row -> "WARNING".equals(row.get("status")))) return "WARNING";
        return "OK";
    }

    private int compareHealthRows(Map<String, Object> left, Map<String, Object> right) {
        int status = Integer.compare(statusRank((String) left.get("status")), statusRank((String) right.get("status")));
        if (status != 0) return status;
        Long rightMinutes = (Long) right.get("minutesStale");
        Long leftMinutes = (Long) left.get("minutesStale");
        if (rightMinutes == null && leftMinutes == null) return 0;
        if (rightMinutes == null) return 1;
        if (leftMinutes == null) return -1;
        return Long.compare(rightMinutes, leftMinutes);
    }

    private int statusRank(String status) {
        return switch (status == null ? "" : status) {
            case "CRITICAL" -> 0;
            case "WARNING" -> 1;
            case "LEARNING" -> 2;
            default -> 3;
        };
    }
}
