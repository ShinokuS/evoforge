package io.github.evoforge.simulation.world.environment.precipitation;

import io.github.evoforge.simulation.world.landscape.terrain.TerrainSurfaceLookup;
import io.github.evoforge.simulation.world.landscape.water.WaterSurfaceLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/**
 * Resolves uniform vertical precipitation against cached top surfaces per XY column.
 *
 * <p>The highest terrain anchor defines which terrain surface is exposed to sky.
 * Positive Water strictly above that terrain becomes the exposed target instead,
 * so rain falling on a lake enters Water directly rather than infiltrating soil
 * underneath the lake. Water at the same Z as a partial terrain Shape retains the
 * terrain-first semantics of that shared anchor cell.
 */
public final class SkyPrecipitationSystem {

    private final TerrainSurfaceLookup terrainSurfaces;
    private final WaterSurfaceLookup waterSurfaces;
    private final PrecipitationSystem precipitation;

    public SkyPrecipitationSystem(
            TerrainSurfaceLookup terrainSurfaces,
            WaterSurfaceLookup waterSurfaces,
            PrecipitationSystem precipitation) {

        if (terrainSurfaces == null) {
            throw new IllegalArgumentException(
                    "terrainSurfaces must not be null");
        }
        if (waterSurfaces == null) {
            throw new IllegalArgumentException(
                    "waterSurfaces must not be null");
        }
        if (precipitation == null) {
            throw new IllegalArgumentException(
                    "precipitation must not be null");
        }

        this.terrainSurfaces = terrainSurfaces;
        this.waterSurfaces = waterSurfaces;
        this.precipitation = precipitation;
    }

    /** Applies the same finite source volume to every currently occupied terrain column. */
    public PrecipitationBatchResult applyUniform(
            int amountPerColumn) {

        int amount = CellVolume.requireValid(amountPerColumn);
        int columns = terrainSurfaces.columnCount();
        if (columns == 0) {
            return PrecipitationBatchResult.empty();
        }

        long[] totals = new long[4];
        terrainSurfaces.forEach((x, y, terrainZ) -> {
            PrecipitationResult result;

            if (waterSurfaces.hasColumn(x, y)
                    && waterSurfaces.topZ(x, y) > terrainZ) {
                result = precipitation.applyWaterSurface(
                        x,
                        y,
                        waterSurfaces.topZ(x, y),
                        amount);
            } else {
                result = precipitation.applyTerrainSurface(
                        x,
                        y,
                        terrainZ,
                        amount);
            }

            totals[0] = Math.addExact(totals[0], result.input());
            totals[1] = Math.addExact(totals[1], result.infiltrated());
            totals[2] = Math.addExact(totals[2], result.surfaceWater());
            totals[3] = Math.addExact(totals[3], result.unplaced());
        });

        return new PrecipitationBatchResult(
                columns,
                totals[0],
                totals[1],
                totals[2],
                totals[3]);
    }
}
