package io.github.evoforge.simulation.world.navigation.traversal;

/** Guaranteed lower bound for every currently valid immediate transition cost. */
public interface TransitionCostLowerBoundLookup {

    long minimumEdgeCostUnits();
}
