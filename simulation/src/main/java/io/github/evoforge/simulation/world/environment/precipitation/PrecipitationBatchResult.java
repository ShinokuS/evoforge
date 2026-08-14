package io.github.evoforge.simulation.world.environment.precipitation;

/** Exact accounting for one precipitation pass across multiple exposed columns. */
public record PrecipitationBatchResult(
        int columns,
        long input,
        long infiltrated,
        long surfaceWater,
        long unplaced) {

    public PrecipitationBatchResult {
        if (columns < 0
                || input < 0L
                || infiltrated < 0L
                || surfaceWater < 0L
                || unplaced < 0L) {
            throw new IllegalArgumentException(
                    "precipitation batch values must not be negative");
        }

        long accounted = Math.addExact(
                Math.addExact(infiltrated, surfaceWater),
                unplaced);
        if (accounted != input) {
            throw new IllegalArgumentException(
                    "precipitation batch must conserve its input");
        }
    }

    public static PrecipitationBatchResult empty() {
        return new PrecipitationBatchResult(0, 0L, 0L, 0L, 0L);
    }
}
