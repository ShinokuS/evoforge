package io.github.evoforge.simulation.world.object.stock.growth;

/** Immutable intrinsic growth pulse independent from environmental conditions. */
public record GrowthDefinition(long baseAmount, long intervalTicks) {
    public GrowthDefinition {
        if (baseAmount <= 0) throw new IllegalArgumentException("baseAmount must be > 0");
        if (intervalTicks <= 0) throw new IllegalArgumentException("intervalTicks must be > 0");
    }
}
