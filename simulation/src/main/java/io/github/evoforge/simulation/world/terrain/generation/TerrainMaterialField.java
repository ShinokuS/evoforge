package io.github.evoforge.simulation.world.terrain.generation;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable generated material identity for every solid Terrain cell. */
public interface TerrainMaterialField {

    WorldBounds bounds();

    /** Returns the semantic material key for an in-bounds generated solid cell. */
    TerrainMaterialKey materialAt(int x, int y, int z);
}
