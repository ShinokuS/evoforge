package io.github.evoforge.simulation.world.environment.precipitation;

/** Resolves one non-negative precipitation source volume for a world XY column. */
@FunctionalInterface
public interface PrecipitationAmountLookup {

    long amountAt(int x, int y);
}
