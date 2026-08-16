package io.github.evoforge.simulation.world.environment.evaporation;

/** Resolves one non-negative potential evaporation demand for a world XY column. */
@FunctionalInterface
public interface EvaporationDemandLookup {

    long amountAt(int x, int y);
}
