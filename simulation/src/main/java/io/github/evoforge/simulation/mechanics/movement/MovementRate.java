package io.github.evoforge.simulation.mechanics.movement;

public record MovementRate(long unitsPerTick) {

    public MovementRate {
        if (unitsPerTick <= 0) {
            throw new IllegalArgumentException(
                    "unitsPerTick must be > 0");
        }
    }

    public static MovementRate of(
            long unitsPerTick) {
        return new MovementRate(unitsPerTick);
    }
}
