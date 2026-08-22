package io.github.evoforge.simulation.world.liquid;

import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.terrain.TerrainLookup;

/**
 * Resolves microtopographic free-liquid retention from supporting terrain.
 *
 * <p>Free liquid may share a cell with partial terrain (for example a Ramp),
 * otherwise the immediately lower terrain cell is the supporting surface.
 */
public final class TerrainSurfaceRetentionLookup
        implements LiquidSurfaceRetentionLookup {

    private final TerrainLookup terrain;
    private final SurfaceRetentionDefinitions definitions;

    public TerrainSurfaceRetentionLookup(
            TerrainLookup terrain,
            SurfaceRetentionDefinitions definitions) {
        if (terrain == null || definitions == null) {
            throw new IllegalArgumentException(
                    "surface retention dependencies must not be null");
        }
        this.terrain = terrain;
        this.definitions = definitions;
    }

    @Override
    public int capacityAt(int x, int y, int z) {
        MaterialDefinitionId current = terrain.find(x, y, z);
        if (current != null) {
            return definitions.getOrZero(current);
        }
        if (z == Integer.MIN_VALUE) {
            return 0;
        }
        return definitions.getOrZero(terrain.find(x, y, z - 1));
    }
}
