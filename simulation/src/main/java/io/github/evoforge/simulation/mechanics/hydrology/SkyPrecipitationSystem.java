package io.github.evoforge.simulation.mechanics.hydrology;

import java.util.TreeSet;

import io.github.evoforge.simulation.world.surface.SkySurface;
import io.github.evoforge.simulation.world.surface.SkySurfaceLookup;
import io.github.evoforge.simulation.world.surface.VerticalSkySurfaceSystem;
import io.github.evoforge.simulation.world.terrain.TerrainSurfaceLookup;
import io.github.evoforge.simulation.world.liquid.water.WaterSurfaceLookup;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;

/** Applies vertical precipitation sources to shared sky-exposed surfaces. */
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

    public SkyPrecipitationSystem(
            TerrainSurfaceLookup terrainSurfaces,
            WaterSurfaceLookup waterSurfaces,
            PrecipitationSystem precipitation) {

        this(
                new VerticalSkySurfaceSystem(
                        terrainSurfaces,
                        waterSurfaces),
                precipitation);
    }

    /** Applies the same finite source volume once to every current sky surface. */
    public PrecipitationBatchResult applyUniform(
            int amountPerColumn) {

        int amount = CellVolume.requireValid(amountPerColumn);
        int[] columns = {0};
        long[] totals = new long[4];

        surfaces.forEach(surface -> {
            PrecipitationResult result = apply(surface, amount);

            columns[0] = Math.incrementExact(columns[0]);
            add(totals, result);
        });

        if (columns[0] == 0) {
            return PrecipitationBatchResult.empty();
        }

        return batch(columns[0], totals);
    }

    /**
     * Applies an independently resolved non-negative source volume to every current
     * state-bearing sky column.
     *
     * <p>Requests may exceed one cell volume. They are realized through the existing
     * per-surface precipitation physics in bounded chunks, re-resolving the exposed
     * surface between chunks so rising Water remains authoritative.
     */
    public PrecipitationBatchResult applyByColumn(
            PrecipitationAmountLookup amounts) {

        if (amounts == null) {
            throw new IllegalArgumentException("amounts must not be null");
        }

        TreeSet<Column> columns = new TreeSet<>();
        surfaces.forEach(surface -> columns.add(
                new Column(surface.x(), surface.y())));

        int appliedColumns = 0;
        long[] totals = new long[4];
        for (Column column : columns) {
            long remaining = amounts.amountAt(column.x(), column.y());
            if (remaining < 0L) {
                throw new IllegalArgumentException(
                        "precipitation amount must be non-negative");
            }
            if (remaining == 0L) {
                continue;
            }

            appliedColumns = Math.incrementExact(appliedColumns);
            while (remaining > 0L) {
                SkySurface surface = surfaces.find(column.x(), column.y());
                if (surface == null) {
                    totals[0] = Math.addExact(totals[0], remaining);
                    totals[3] = Math.addExact(totals[3], remaining);
                    break;
                }

                int chunk = (int) Math.min(
                        remaining,
                        (long) CellVolume.FULL);
                PrecipitationResult result = apply(surface, chunk);
                add(totals, result);
                remaining -= chunk;

                if (result.unplaced() > CellVolume.EMPTY) {
                    totals[0] = Math.addExact(totals[0], remaining);
                    totals[3] = Math.addExact(totals[3], remaining);
                    break;
                }
            }
        }

        return appliedColumns == 0
                ? PrecipitationBatchResult.empty()
                : batch(appliedColumns, totals);
    }

    private PrecipitationResult apply(
            SkySurface surface,
            int amount) {
        return surface.kind() == SkySurface.Kind.WATER
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
    }

    private static PrecipitationBatchResult batch(
            int columns,
            long[] totals) {
        return new PrecipitationBatchResult(
                columns,
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

    private record Column(int x, int y)
            implements Comparable<Column> {

        @Override
        public int compareTo(Column other) {
            int xOrder = Integer.compare(x, other.x);
            return xOrder != 0
                    ? xOrder
                    : Integer.compare(y, other.y);
        }
    }
}
