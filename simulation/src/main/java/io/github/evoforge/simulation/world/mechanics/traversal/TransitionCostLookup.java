package io.github.evoforge.simulation.world.mechanics.traversal;

public interface TransitionCostLookup {

    TransitionCost cost(
            int fromX,
            int fromY,
            int fromZ,
            int toX,
            int toY,
            int toZ);
}
