package com.crypto.client.binance.websocket;

import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;

/** FIX-131: read-only observation, separate from FIX-043 recovery and from the callback.
 * Eight streams per pass, one worker, 2s query timeout, 72h maximum retained horizon.
 * A missing closed row is a DB coverage fact, not proof of a WebSocket root cause. */
@Component
public class CandleGapDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(CandleGapDiagnostics.class);
    record Stream(String symbol, String interval) {}
    record Window(Instant since, long seconds) {}
    record Range(Instant from, Instant through, int count) {}
    private final JdbcTemplate jdbc;
    private final Map<Stream, Window> subscriptions = new LinkedHashMap<>();
    private final Map<Stream, Set<Instant>> previous = new HashMap<>();
    private final ScheduledExecutorService scanner;
    private int cursor;
    public CandleGapDiagnostics(DataSource source) {
        jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(2);
        jdbc.setMaxRows(5000);
        scanner = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread t = new Thread(task, "fix131-gap-scan"); t.setDaemon(true); return t;
        });
        scanner.scheduleWithFixedDelay(this::scanSafely, 10, 10, TimeUnit.SECONDS);
    }
    /** Preserve observation start across reconnects; reset only for genuinely removed/re-added streams. */
    synchronized void subscribed(String url, Instant at) {
        if (url == null || !url.contains("streams=")) return;
        Set<Stream> wanted = new LinkedHashSet<>();
        for (String part : url.substring(url.indexOf("streams=") + 8).split("/")) {
            String[] pair = part.split("@kline_");
            if (pair.length != 2) continue;
            long seconds = intervalSeconds(pair[1]);
            if (seconds == 0) {
                log.warn("[FIX-131][GAP_SCAN_UNSUPPORTED_INTERVAL] interval={}", pair[1]); continue;
            }
            Stream stream = new Stream(pair[0].toUpperCase(Locale.ROOT), pair[1]);
            wanted.add(stream);
            subscriptions.putIfAbsent(stream, new Window(at, seconds));
        }
        subscriptions.keySet().retainAll(wanted);
        previous.keySet().retainAll(wanted);
    }
    static long intervalSeconds(String interval) {
        // Only fixed-duration, UTC-epoch-aligned intervals. Calendar months/weeks require separate alignment.
        return switch (interval) {
            case "1m" -> 60; case "3m" -> 180; case "5m" -> 300; case "15m" -> 900;
            case "30m" -> 1800; case "1h" -> 3600; case "2h" -> 7200; case "4h" -> 14400;
            case "6h" -> 21600; case "8h" -> 28800; case "12h" -> 43200; case "1d" -> 86400;
            default -> 0;
        };
    }
    static Set<Instant> missing(Instant since, Instant now, long seconds, Set<Instant> closed) {
        Set<Instant> result = new TreeSet<>();
        // Exclude any candle already forming when subscription began; 30s grace for close arrival/commit.
        long first = Math.floorDiv(since.getEpochSecond(), seconds) * seconds;
        if (since.isAfter(Instant.ofEpochSecond(first))) first += seconds;
        long horizon = Math.floorDiv(now.minusSeconds(72 * 3600).getEpochSecond(), seconds) * seconds;
        first = Math.max(first, horizon);
        long last = Math.floorDiv(now.minusSeconds(30).getEpochSecond(), seconds) * seconds - seconds;
        for (long open = first; open <= last; open += seconds) {
            Instant identity = Instant.ofEpochSecond(open);
            if (!closed.contains(identity)) result.add(identity);
        }
        return result;
    }
    static List<Range> ranges(Set<Instant> points, long seconds) {
        List<Range> result = new ArrayList<>();
        Instant first = null, last = null; int count = 0;
        for (Instant point : new TreeSet<>(points)) {
            if (last != null && !last.plusSeconds(seconds).equals(point)) {
                result.add(new Range(first, last, count)); first = null; count = 0;
            }
            if (first == null) first = point;
            last = point; count++;
        }
        if (first != null) result.add(new Range(first, last, count));
        return result;
    }
    private void scanSafely() {
        try { scan(); }
        catch (Exception ex) { log.warn("[FIX-131][GAP_SCAN_FAILED] diagnostic only; no recovery performed", ex); }
    }
    private void scan() {
        List<Map.Entry<Stream, Window>> batch;
        synchronized (this) {
            var all = new ArrayList<>(subscriptions.entrySet());
            if (all.isEmpty()) return;
            batch = new ArrayList<>();
            for (int n = 0; n < Math.min(8, all.size()); n++) batch.add(all.get((cursor + n) % all.size()));
            cursor = (cursor + batch.size()) % all.size();
        }
        for (var entry : batch) {
            try {
                var stream = entry.getKey(); var window = entry.getValue(); Instant now = Instant.now();
                Instant from = window.since().isAfter(now.minusSeconds(72 * 3600)) ? window.since() : now.minusSeconds(72 * 3600 + window.seconds());
                Set<Instant> closed = new HashSet<>(jdbc.query("SELECT open_time FROM crypto_ai.candle WHERE symbol=? AND interval_code=? AND closed=1 AND open_time>=? AND open_time<=? ORDER BY open_time",
                        ps -> {
                            var utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                            ps.setString(1, stream.symbol()); ps.setString(2, stream.interval());
                            ps.setTimestamp(3, Timestamp.from(from), utc); ps.setTimestamp(4, Timestamp.from(now), utc);
                        }, (rs, row) -> rs.getTimestamp(1, Calendar.getInstance(TimeZone.getTimeZone("UTC"))).toInstant()));
                var missing = missing(window.since(), now, window.seconds(), closed);
                Set<Instant> added, resolved, expired;
                synchronized (this) {
                    if (!window.equals(subscriptions.get(stream))) continue;
                    var old = previous.getOrDefault(stream, Set.of());
                    added = new TreeSet<>(missing); added.removeAll(old);
                    resolved = new TreeSet<>(old); resolved.retainAll(closed);
                    expired = new TreeSet<>(old); expired.removeAll(missing); expired.removeAll(closed);
                    previous.put(stream, missing);
                }
                // Logging outside the subscription monitor cannot hold up lifecycle activation.
                report("DB_CANDLE_GAP", stream, added, window.seconds());
                report("DB_CANDLE_GAP_RESOLVED", stream, resolved, window.seconds());
                report("GAP_OUTSIDE_SCAN_HORIZON", stream, expired, window.seconds());
            } catch (Exception ex) { log.warn("[FIX-131][GAP_SCAN_FAILED] stream={}; coverage unknown, no recovery performed", entry.getKey(), ex); }
        }
    }
    private void report(String event, Stream stream, Set<Instant> points, long seconds) {
        for (var range : ranges(points, seconds)) log.warn("[FIX-131][{}] symbol={}, interval={}, fromOpenTime={}, throughOpenTime={}, count={}, detectedAt={}, repair=false",
                event, stream.symbol(), stream.interval(), range.from(), range.through(), range.count(), Instant.now());
    }
    @PreDestroy public void stop() { scanner.shutdownNow(); }
}
