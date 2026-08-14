package io.github.evoforge.simulation.world.environment.precipitation;

import io.github.evoforge.simulation.world.environment.sky.SkySurface;
import io.github.evoforge.simulation.world.environment.sky.SkySurfaceLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/** Applies uniform vertical precipitation to shared sky-exposed surfaces. */
public final class SkyPrecipitationSystem {

    private final SkySurfaceLookup surfaces;
    private final PrecipitationSystem precipitation;

    public SkyPrecipitationSystem(
            SkySurfaceLookup surfaces,
            PrecipitationSystem precipitation) {

        if (surfaces == null) {
            throw new IllegalArgumentException(
                    "surfaces must not be null");
        }
        if (precipitation == null) {
            throw new IllegalArgumentException(
                    "precipitation must not be null");
        }

        this.surfaces = surfaces;
        this.precipitation = precipitation;
    }

    /** Applies the same finite source volume once to every current sky surface. */
    public PrecipitationBatchResult applyUniform(
            int amountPerColumn) {

        int amount = CellVolume.requireValid(amountPerColumn);
        int[] columns = {0};
        long[] totals = new long[4];

        surfaces.forEach(surface -> {
            PrecipitationResult result =
                    surface.kind() == SkySurface.Kind.WATER
                            ? precipitation.applyWaterSurface(
                                    surface.x(),
                                    surface.y(),
                                    surface.z(),
                                    amount)
                            : precipitation.applyTerrainSurface(
                                    surface.x(),
                                    surface.y(),
                                    surface.z(),
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
