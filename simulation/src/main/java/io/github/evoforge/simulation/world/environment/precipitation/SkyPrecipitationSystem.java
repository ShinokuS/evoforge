package io.github.evoforge.simulation.world.environment.precipitation;

import io.github.evoforge.simulation.world.landscape.terrain.TerrainSurfaceLookup;
import io.github.evoforge.simulation.world.landscape.water.WaterSurfaceLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/**
 * Resolves uniform vertical precipitation against cached top surfaces per XY column.
 *
 * <p>The target domain is the deterministic union of occupied Terrain columns and
 * wet Water columns. Positive Water strictly above terrain becomes the exposed target,
 * so rain falling on a lake enters Water directly rather than infiltrating soil
 * underneath the lake. A wet column without terrain is also sky-addressable, allowing
 * runoff or a waterfall over empty space to continue receiving precipitation.
 * Water at the same Z as a partial terrain Shape retains terrain-first semantics of
 * that shared anchor cell.
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

    /**
     * Applies the same finite source volume once to every currently sky-addressable
     * Terrain/Water XY column.
     */
    public PrecipitationBatchResult applyUniform(
            int amountPerColumn) {

        int amount = CellVolume.requireValid(amountPerColumn);
        int[] columns = {0};
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

            columns[0] = Math.incrementExact(columns[0]);
            add(totals, result);
        });

        waterSurfaces.forEach((x, y, waterZ) -> {
            if (terrainSurfaces.hasColumn(x, y)) {
                return;
            }

            PrecipitationResult result =
                    precipitation.applyWaterSurface(
                            x,
                            y,
                            waterZ,
                            amount);
            columns[0] = Math.incrementExact(columns[0]);
            add(totals, result);
        });

        if (columns[0] == 0) {
            return PrecipitationBatchResult.empty();
        }

        return new PrecipitationBatchResult(
                columns[0],
                totals[0],
                totals[1],
                totals[2],
                totals[3]);
    }

    private static void add(
            long[] totals,
            PrecipitationResult result) {

        totals[0] = Math.addExact(totals[0], result.input());
        totals[1] = Math.addExact(totals[1], result.infiltrated());
        totals[2] = Math.addExact(totals[2], result.surfaceWater());
        totals[3] = Math.addExact(totals[3], result.unplaced());
    }
}
