package io.github.evoforge.simulation.world.agent.perception.vision;

import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;

/** Initial sight-occlusion adapter: terrain occupying a sampled sight cell is opaque. */
public final class TerrainSightOcclusionLookup implements SightOcclusionLookup {
    private final TerrainLookup terrain;

    public TerrainSightOcclusionLookup(TerrainLookup terrain) {
        if (terrain == null) throw new IllegalArgumentException("terrain must not be null");
        this.terrain = terrain;
    }

    @Override public boolean blocksSight(int x, int y, int z) {
        return terrain.contains(x, y, z);
    }
}
