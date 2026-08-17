package io.github.evoforge.simulation.world.atlas;

/**
 * Runtime hydro-climate forcing whose current rates may change between ticks.
 *
 * <p>The field advances exactly once before atmospheric source/sink requests are read for a tick and
 * exposes already integrated whole CellVolume amounts for that interval. Static generated climate
 * fields remain plain {@link HydroClimateField} instances.</p>
 */
public interface DynamicHydroClimateField extends HydroClimateField {

    void advanceToTick(long tick);

    long precipitationDueAt(int x, int y);

    long evaporativeDemandDueAt(int x, int y);
}
