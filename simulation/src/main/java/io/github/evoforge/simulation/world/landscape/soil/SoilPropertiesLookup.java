package io.github.evoforge.simulation.world.landscape.soil;

@FunctionalInterface
public interface SoilPropertiesLookup {

    /** Returns effective local porous properties, or null for non-absorbing terrain. */
    SoilProperties find(int x, int y, int z);
}
