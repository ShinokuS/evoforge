package io.github.evoforge.simulation.world.materialization;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.landscape.LandscapeMutations;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainExtentLookup;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainPlacementResult;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * One-way initialization bridge from generated elevation facts into runtime Terrain.
 *
 * <p>The materializer owns neither source facts nor resulting Terrain. It requires an
 * empty runtime Terrain, resolves one material for every generated solid cell, and
 * delegates every mutation through {@link LandscapeMutations}.</p>
 */
public final class WorldTerrainMaterializer {

    private final ElevationField elevation;
    private final TerrainMaterialResolver materials;
    private final TerrainExtentLookup terrainExtents;
    private final LandscapeMutations landscape;

    public WorldTerrainMaterializer(
            ElevationField elevation,
            TerrainMaterialResolver materials,
            TerrainExtentLookup terrainExtents,
            LandscapeMutations landscape) {

        if (elevation == null
                || materials == null
                || terrainExtents == null
                || landscape == null) {
            throw new IllegalArgumentException(
                    "terrain materialization dependencies must not be null");
        }

        this.elevation = elevation;
        this.materials = materials;
        this.terrainExtents = terrainExtents;
        this.landscape = landscape;
    }

    /**
     * Materializes every XY column from the world floor through its Atlas surface,
     * inclusive. The target must be an empty Terrain.
     */
    public TerrainMaterializationResult materialize() {
        if (!terrainExtents.empty()) {
            throw new IllegalStateException(
                    "generated terrain materialization requires empty Terrain");
        }

        WorldBounds bounds = elevation.bounds();
        validateSurfaceHeights(bounds);

        long columns = 0L;
        long terrainCells = 0L;

        for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
            int worldX = (int) x;
            for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
                int worldY = (int) y;
                int surfaceZ = elevation.elevationAt(worldX, worldY);

                for (long z = bounds.minZ(); z <= (long) surfaceZ; z++) {
                    int worldZ = (int) z;
                    LandscapeDefinitionId material =
                            materials.materialAt(worldX, worldY, worldZ);
                    if (material == null) {
                        throw new IllegalStateException(
                                "terrain material resolver returned null at "
                                        + coordinate(worldX, worldY, worldZ));
                    }

                    TerrainPlacementResult result = landscape.placeTerrain(
                            worldX,
                            worldY,
                            worldZ,
                            material);
                    if (!result.accepted()) {
                        throw new IllegalStateException(
                                "terrain materialization placement rejected at "
                                        + coordinate(worldX, worldY, worldZ)
                                        + ": " + result.code());
                    }
                    terrainCells = Math.addExact(terrainCells, 1L);
                }

                columns = Math.addExact(columns, 1L);
            }
        }

        return new TerrainMaterializationResult(columns, terrainCells);
    }

    private void validateSurfaceHeights(WorldBounds bounds) {
        for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
            int worldX = (int) x;
            for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
                int worldY = (int) y;
                int surfaceZ = elevation.elevationAt(worldX, worldY);
                if (surfaceZ < bounds.minZ() || surfaceZ > bounds.maxZ()) {
                    throw new IllegalStateException(
                            "elevation surface outside world bounds at "
                                    + coordinate(worldX, worldY, surfaceZ));
                }
            }
        }
    }

    private static String coordinate(int x, int y, int z) {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
