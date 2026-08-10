package com.crypto.execution.service;

import com.crypto.domain.TradeSignal;
import com.crypto.execution.domain.ExecutionOpportunity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Thread-bound data/persistence override used only by Administration historical replay.
 *
 * The execution algorithms remain the production services. While this scope is active,
 * their signal lookups are resolved exclusively from replay-generated signals as-of the
 * current replay timestamp, and opportunity persistence is redirected to the supplied
 * shadow sink. Production repositories are never read/written by the scoped execution path.
 */
@Component
public class ExecutionReplayScope {

    private final ThreadLocal<State> state = new ThreadLocal<>();

    public Scope open(long runId, List<TradeSignal> replaySignals, Consumer<ExecutionOpportunity> opportunitySink) {
        if (state.get() != null) throw new IllegalStateException("Execution replay scope is already active on this thread");
        List<TradeSignal> immutable = replaySignals == null ? List.of() : replaySignals.stream()
                .filter(s -> s != null && s.getGeneratedAt() != null)
                .sorted(Comparator.comparing(TradeSignal::getGeneratedAt))
                .toList();
        state.set(new State(runId, immutable, opportunitySink));
        return new Scope();
    }

    public boolean active() { return state.get() != null; }
    public long runId() { return required().runId; }

    public void reference(Instant reference) { required().reference = reference; }

    public List<TradeSignal> recent(String symbol, String interval, Instant reference, int limit) {
        State s = required();
        Instant ref = reference != null ? reference : s.reference;
        if (ref == null) return List.of();
        return s.signals.stream()
                .filter(x -> symbol.equals(x.getSymbol()) && interval.equals(x.getInterval()))
                .filter(x -> !x.getGeneratedAt().isAfter(ref))
                .sorted(Comparator.comparing(TradeSignal::getGeneratedAt).reversed())
                .limit(limit)
                .toList();
    }

    public Optional<TradeSignal> latestAtOrBefore(String symbol, String interval, Instant reference) {
        return recent(symbol, interval, reference, 1).stream().findFirst();
    }

    public Optional<TradeSignal> previousBefore(String symbol, String interval, Instant reference) {
        State s = required();
        return s.signals.stream()
                .filter(x -> symbol.equals(x.getSymbol()) && interval.equals(x.getInterval()))
                .filter(x -> x.getGeneratedAt().isBefore(reference))
                .max(Comparator.comparing(TradeSignal::getGeneratedAt));
    }

    public Optional<ExecutionOpportunity> currentOpportunity(String symbol, List<String> statuses) {
        ExecutionOpportunity o = required().opportunity;
        if (o == null || !symbol.equals(o.getSymbol())) return Optional.empty();
        if (statuses != null && !statuses.isEmpty() && !statuses.contains(o.getStatus())) return Optional.empty();
        return Optional.of(o);
    }

    public ExecutionOpportunity saveOpportunity(ExecutionOpportunity opportunity) {
        State s = required();
        s.opportunity = opportunity;
        if (s.opportunitySink != null) s.opportunitySink.accept(opportunity);
        return opportunity;
    }

    public final class Scope implements AutoCloseable {
        private boolean closed;
        @Override public void close() {
            if (!closed) {
                state.remove();
                closed = true;
            }
        }
    }

    private State required() {
        State s = state.get();
        if (s == null) throw new IllegalStateException("No Administration execution replay scope is active");
        return s;
    }

    private static final class State {
        final long runId;
        final List<TradeSignal> signals;
        final Consumer<ExecutionOpportunity> opportunitySink;
        Instant reference;
        ExecutionOpportunity opportunity;
        State(long runId, List<TradeSignal> signals, Consumer<ExecutionOpportunity> opportunitySink) {
            this.runId = runId;
            this.signals = new ArrayList<>(signals);
            this.opportunitySink = opportunitySink;
        }
    }
}
