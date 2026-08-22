package io.github.evoforge.simulation.time;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Keeps current state plus a bounded recent delta tail.
 *
 * <p>This is an in-memory longevity primitive, not persistence. When the tail reaches its configured
 * limit, all deltas are folded into a new checkpoint and the historical tail is discarded.</p>
 */
public final class CompactingStateBuffer<S, D> {

    private final BiFunction<S, D, S> reducer;
    private final int maxTailEntries;
    private final ArrayList<D> tail = new ArrayList<>();

    private S checkpoint;
    private S current;
    private long appliedDeltas;
    private long compactions;

    public CompactingStateBuffer(
            S initialState,
            BiFunction<S, D, S> reducer,
            int maxTailEntries) {
        if (initialState == null || reducer == null) {
            throw new IllegalArgumentException("initialState and reducer must not be null");
        }
        if (maxTailEntries <= 0) {
            throw new IllegalArgumentException("maxTailEntries must be > 0");
        }
        this.checkpoint = initialState;
        this.current = initialState;
        this.reducer = reducer;
        this.maxTailEntries = maxTailEntries;
    }

    public void append(D delta) {
        if (delta == null) {
            throw new IllegalArgumentException("delta must not be null");
        }
        current = reducer.apply(current, delta);
        tail.add(delta);
        appliedDeltas++;
        if (tail.size() >= maxTailEntries) {
            compact();
        }
    }

    public void compact() {
        if (tail.isEmpty()) {
            return;
        }
        checkpoint = current;
        tail.clear();
        compactions++;
    }

    public S currentState() {
        return current;
    }

    public S checkpointState() {
        return checkpoint;
    }

    public List<D> deltaTail() {
        return List.copyOf(tail);
    }

    public int tailSize() {
        return tail.size();
    }

    public int maxTailEntries() {
        return maxTailEntries;
    }

    public long appliedDeltas() {
        return appliedDeltas;
    }

    public long compactions() {
        return compactions;
    }
}
