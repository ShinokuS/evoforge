package io.github.evoforge.simulation.world.environment.evaporation;

import java.util.Comparator;
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
 * percentage. Free Water is removed before retained Water. Retained Water is then removed
 * top-down through the contiguous porous Soil column exposed to the atmosphere. A non-porous
 * layer terminates that path. Other retained liquid constituents are neither candidates nor
 * sinks of this Water-specific process.
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

        return applyByColumn((x, y) -> amount);
    }

    /**
     * Applies independently resolved potential evaporation demand to every currently
     * wet state-bearing column.
     *
     * <p>Requests may exceed one cell volume. The existing per-column evaporation
     * physics realizes them in bounded chunks. Once a chunk cannot be fulfilled,
     * the column is exhausted for this instant and the unresolved tail is accounted
     * as unfulfilled without synthetic extra work.
     */
    public EvaporationBatchResult applyByColumn(
            EvaporationDemandLookup demands) {
        if (demands == null) {
            throw new IllegalArgumentException("demands must not be null");
        }

        TreeSet<Column> columns = wetColumns();
        if (columns.isEmpty()) {
            return EvaporationBatchResult.empty();
        }

        int appliedColumns = 0;
        long requested = 0L;
        long waterRemoved = 0L;
        long retainedWaterRemoved = 0L;
        long unfulfilled = 0L;

        for (Column column : columns) {
            long remaining = demands.amountAt(column.x(), column.y());
            if (remaining < 0L) {
                throw new IllegalArgumentException(
                        "evaporation demand must be non-negative");
            }
            if (remaining == 0L) {
                continue;
            }

            appliedColumns = Math.incrementExact(appliedColumns);
            while (remaining > 0L) {
                int chunk = (int) Math.min(
                        remaining,
                        (long) CellVolume.FULL);
                EvaporationResult result = applyColumn(
                        column.x(),
                        column.y(),
                        chunk);

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
                remaining -= chunk;

                if (result.unfulfilled() > CellVolume.EMPTY) {
                    requested = Math.addExact(requested, remaining);
                    unfulfilled = Math.addExact(unfulfilled, remaining);
                    break;
                }
            }
        }

        return appliedColumns == 0
                ? EvaporationBatchResult.empty()
                : new EvaporationBatchResult(
                        appliedColumns,
                        requested,
                        waterRemoved,
                        retainedWaterRemoved,
                        unfulfilled);
    }

    private TreeSet<Column> wetColumns() {
        TreeSet<Column> columns = new TreeSet<>();
        waterSurfaces.forEach((x, y, z) -> columns.add(new Column(x, y)));
        retainedCells.forEach(
                WaterSystem.TYPE,
                (x, y, z) -> columns.add(new Column(x, y)));
        return columns;
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
                    int removed = removeRetainedFromConnectedSoilColumn(
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

    private int removeRetainedFromConnectedSoilColumn(
            int x,
            int y,
            int surfaceZ,
            int requested) {
        TreeSet<Integer> retainedZ = new TreeSet<>(Comparator.reverseOrder());
        retainedCells.forEach(
                WaterSystem.TYPE,
                (cellX, cellY, cellZ) -> {
                    if (cellX == x && cellY == y && cellZ <= surfaceZ) {
                        retainedZ.add(cellZ);
                    }
                });

        int remaining = requested;
        int removed = CellVolume.EMPTY;
        int connectedDownTo = surfaceZ;
        for (int z : retainedZ) {
            if (!porousBetween(x, y, connectedDownTo, z)) {
                break;
            }
            int amount = soilLiquids.removeAtMost(
                    WaterSystem.TYPE,
                    x,
                    y,
                    z,
                    remaining);
            removed = Math.addExact(removed, amount);
            remaining -= amount;
            connectedDownTo = z - 1;
            if (remaining == CellVolume.EMPTY) {
                break;
            }
        }
        return removed;
    }

    private boolean porousBetween(int x, int y, int fromZ, int toZ) {
        for (int z = fromZ; z >= toZ; z--) {
            if (soilLiquids.propertiesAt(x, y, z) == null) {
                return false;
            }
        }
        return true;
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
