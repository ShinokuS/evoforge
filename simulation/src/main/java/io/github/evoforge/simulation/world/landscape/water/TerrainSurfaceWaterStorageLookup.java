package io.github.evoforge.simulation.world.landscape.water;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;

/**
 * Resolves surface-storage capacity from the terrain supporting a Water cell.
 *
 * <p>Water may share a cell with partial terrain (for example a Ramp), otherwise
 * the immediately lower terrain cell is the supporting surface. Water suspended
 * without local terrain support has no surface storage.
 */
public final class TerrainSurfaceWaterStorageLookup
        implements SurfaceWaterStorageLookup {

    private final TerrainLookup terrain;
    private final SurfaceWaterStorageDefinitions definitions;

    public TerrainSurfaceWaterStorageLookup(
            TerrainLookup terrain,
            SurfaceWaterStorageDefinitions definitions) {

        if (terrain == null || definitions == null) {
            throw new IllegalArgumentException(
                    "surface water storage dependencies must not be null");
        }
        this.terrain = terrain;
        this.definitions = definitions;
    }

    @Override
    public int capacityAtWaterCell(
            int x,
            int y,
            int z) {

        LandscapeDefinitionId current = terrain.find(x, y, z);
        if (current != null) {
            return definitions.getOrZero(current);
        }
        if (z == Integer.MIN_VALUE) {
            return 0;
        }
        return definitions.getOrZero(
                terrain.find(x, y, z - 1));
    }
}
