package io.github.evoforge.simulation.world.mechanics.traversal;

public record TransitionCost(long units) {

    public TransitionCost {
        if (units <= 0) {
            throw new IllegalArgumentException(
                    "transition cost must be > 0");
        }
    }

    public static TransitionCost of(
            long units) {

        return new TransitionCost(units);
    }
}
