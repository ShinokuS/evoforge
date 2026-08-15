package io.github.evoforge.simulation.world.landscape.soil;

/** Read-only local hydrologic properties of one terrain cell. */
@FunctionalInterface
public interface SoilHydrologyLookup {

    /** Returns local properties, or {@code null} when the terrain does not absorb Water. */
    SoilHydrology find(int x, int y, int z);
}
