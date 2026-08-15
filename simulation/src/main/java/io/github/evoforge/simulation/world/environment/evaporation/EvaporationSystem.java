package io.github.evoforge.simulation.world.environment.evaporation;

import java.util.TreeSet;

import io.github.evoforge.simulation.world.environment.sky.SkySurface;
import io.github.evoforge.simulation.world.environment.sky.SkySurfaceLookup;
import io.github.evoforge.simulation.world.landscape.soil.SoilLiquidCellsLookup;
import io.github.evoforge.simulation.world.landscape.soil.SoilLiquidSystem;
import io.github.evoforge.simulation.world.landscape.water.WaterSurfaceLookup;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.mechanics.geometry.CellFace;
import io.github.evoforge.simulation.world.mechanics.geometry.CellSpace;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

/**
 * Simple deterministic Water evaporation sink over currently wet state-bearing columns.
 *
 * <p>The configured amount is an absolute Water volume per exposed XY column, not a
 * percentage. Free Water is removed before retained Water. Other retained liquid
 * constituents are neither candidates nor sinks of this Water-specific process.
 */
public final class EvaporationSystem {

    private final SkySurfaceLookup skySurfaces;
    private final WaterSurfaceLookup waterSurfaces;
    private final SoilLiquidCellsLookup retainedCells;
    private final GeometryLookup geometry;
    private final WaterSystem water;
    private final SoilLiquidSystem soilLiquids;

    public EvaporationSystem(
            SkySurfaceLookup skySurfaces,
            WaterSurfaceLookup waterSurfaces,
            SoilLiquidCellsLookup retainedCells,
            GeometryLookup geometry,
            WaterSystem water,
            SoilLiquidSystem soilLiquids) {

        if (skySurfaces == null
                || waterSurfaces == null
                || retainedCells == null
                || geometry == null
                || water == null
                || soilLiquids == null) {
            throw new IllegalArgumentException(
                    "evaporation dependencies must not be null");
        }

        this.skySurfaces = skySurfaces;
        this.waterSurfaces = waterSurfaces;
        this.retainedCells = retainedCells;
        this.geometry = geometry;
        this.water = water;
        this.soilLiquids = soilLiquids;
    }

    /**
     * Removes at most {@code amountPerColumn} of Water from every currently wet
     * candidate column and returns exact sink accounting.
     */
    public EvaporationBatchResult applyUniform(int amountPerColumn) {
        int amount = CellVolume.requireValid(amountPerColumn);
        if (amount == CellVolume.EMPTY) {
            return EvaporationBatchResult.empty();
        }

        TreeSet<Column> columns = new TreeSet<>();
        waterSurfaces.forEach((x, y, z) -> columns.add(new Column(x, y)));
        retainedCells.forEach(
                WaterSystem.TYPE,
                (x, y, z) -> columns.add(new Column(x, y)));

        if (columns.isEmpty()) {
            return EvaporationBatchResult.empty();
        }

        long requested = 0L;
        long waterRemoved = 0L;
        long retainedWaterRemoved = 0L;
        long unfulfilled = 0L;

        for (Column column : columns) {
            EvaporationResult result = applyColumn(
                    column.x(),
                    column.y(),
                    amount);
            requested = Math.addExact(requested, result.requested());
            waterRemoved = Math.addExact(
                    waterRemoved,
                    result.surfaceWaterRemoved());
            retainedWaterRemoved = Math.addExact(
                    retainedWaterRemoved,
                    result.retainedWaterRemoved());
            unfulfilled = Math.addExact(
                    unfulfilled,
                    result.unfulfilled());
        }

        return new EvaporationBatchResult(
                columns.size(),
                requested,
                waterRemoved,
                retainedWaterRemoved,
                unfulfilled);
    }

    private EvaporationResult applyColumn(
            int x,
            int y,
            int requested) {

        int remaining = requested;
        int waterRemoved = CellVolume.EMPTY;
        int retainedWaterRemoved = CellVolume.EMPTY;

        while (remaining > CellVolume.EMPTY) {
            SkySurface surface = skySurfaces.find(x, y);
            if (surface == null) {
                break;
            }

            int removedThisPass = CellVolume.EMPTY;
            if (surface.kind() == SkySurface.Kind.WATER) {
                int removed = water.removeAtMost(
                        x,
                        y,
                        surface.z(),
                        remaining);
                waterRemoved += removed;
                remaining -= removed;
                removedThisPass += removed;
            } else {
                if (opensFromAbove(geometry.find(x, y, surface.z()))) {
                    int removed = water.removeAtMost(
                            x,
                            y,
                            surface.z(),
                            remaining);
                    waterRemoved += removed;
                    remaining -= removed;
                    removedThisPass += removed;
                }

                if (remaining > CellVolume.EMPTY) {
                    int removed = soilLiquids.removeAtMost(
                            WaterSystem.TYPE,
                            x,
                            y,
                            surface.z(),
                            remaining);
                    retainedWaterRemoved += removed;
                    remaining -= removed;
                    removedThisPass += removed;
                }
            }

            if (removedThisPass == CellVolume.EMPTY) {
                break;
            }
        }

        return new EvaporationResult(
                requested,
                waterRemoved,
                retainedWaterRemoved,
                remaining);
    }

    private static boolean opensFromAbove(Shape shape) {
        return CellSpace.boundaryOpeningFloor(
                shape,
                CellFace.POSITIVE_Z) != CellSpace.CLOSED;
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
