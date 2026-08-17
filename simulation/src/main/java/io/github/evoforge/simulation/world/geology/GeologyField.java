package io.github.evoforge.simulation.world.geology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable generated geological facts across the finite world volume. */
public interface GeologyField {
    WorldBounds bounds();

    CompiledGeologyProfile profile();

    /** Stable generated rock-unit identity at one world cell. */
    GeologyUnitKey unitAt(int x, int y, int z);

    /** Stable macro-province identity used for diagnostics and downstream regional reasoning. */
    long provinceIdAt(int x, int y);

    default GeologyMaterialKey materialAt(int x, int y, int z) {
        return profile().materialFor(unitAt(x, y, z));
    }

    default boolean contains(int x, int y, int z) {
        WorldBounds bounds = bounds();
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY()
                && z >= bounds.minZ() && z <= bounds.maxZ();
    }
}
