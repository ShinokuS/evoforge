package io.github.evoforge.simulation.world.mechanics.growth;

/** Last completed growth evaluation for one object. */
public record GrowthTrace(
        long tick,
        long resolvedAmount,
        long appliedAmount,
        long quantityAfter,
        long capacity) {
    public GrowthTrace {
        if (tick < 0) throw new IllegalArgumentException("tick must be >= 0");
        if (resolvedAmount < 0 || appliedAmount < 0) {
            throw new IllegalArgumentException("growth amounts must be >= 0");
        }
        if (quantityAfter < 0 || capacity <= 0 || quantityAfter > capacity) {
            throw new IllegalArgumentException("invalid stock state in growth trace");
        }
    }
}
