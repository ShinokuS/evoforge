package io.github.evoforge.simulation.world.environment.evaporation;

/** Exact sink accounting across one deterministic evaporation pass. */
public record EvaporationBatchResult(
        int columns,
        long requested,
        long surfaceWaterRemoved,
        long soilMoistureRemoved,
        long unfulfilled) {

    public EvaporationBatchResult {
        if (columns < 0
                || requested < 0L
                || surfaceWaterRemoved < 0L
                || soilMoistureRemoved < 0L
                || unfulfilled < 0L) {
            throw new IllegalArgumentException(
                    "evaporation batch values must not be negative");
        }

        long accounted = Math.addExact(
                Math.addExact(surfaceWaterRemoved, soilMoistureRemoved),
                unfulfilled);
        if (accounted != requested) {
            throw new IllegalArgumentException(
                    "evaporation batch accounting mismatch: "
                            + accounted
                            + " != "
                            + requested);
        }
    }

    public static EvaporationBatchResult empty() {
        return new EvaporationBatchResult(0, 0L, 0L, 0L, 0L);
    }

    public long removed() {
        return surfaceWaterRemoved + soilMoistureRemoved;
    }
}
