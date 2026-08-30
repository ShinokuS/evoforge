package io.github.evoforge.simulation.world.terrain.genesis;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Lightweight opt-in timing for the accepted V15 pipeline. */
public final class V15GenerationProfiler {
    public static final String ENABLE_PROPERTY = "evoforge.v15.profile";

    private static final ConcurrentHashMap<String, Counter> COUNTERS = new ConcurrentHashMap<>();

    private V15GenerationProfiler() {}

    public static <T> T measure(String stage, long logicalCells, Supplier<T> action) {
        if (stage == null || action == null) {
            throw new IllegalArgumentException("V15 profile stage/action must not be null");
        }
        long started = System.nanoTime();
        try {
            return action.get();
        } finally {
            record(stage, logicalCells, System.nanoTime() - started);
        }
    }

    public static void record(String stage, long logicalCells, long elapsedNanos) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        Counter counter = COUNTERS.computeIfAbsent(stage, ignored -> new Counter());
        long invocation = counter.invocations.incrementAndGet();
        long totalNanos = counter.totalNanos.addAndGet(elapsedNanos);
        System.out.printf(
                Locale.ROOT,
                "v15-stage stage=%s invocation=%d logicalCells=%d elapsedMs=%.3f totalMs=%.3f%n",
                stage,
                invocation,
                logicalCells,
                elapsedNanos / 1_000_000d,
                totalNanos / 1_000_000d);
    }

    public static void reset() {
        COUNTERS.clear();
    }

    private static final class Counter {
        private final AtomicLong invocations = new AtomicLong();
        private final AtomicLong totalNanos = new AtomicLong();
    }
}
