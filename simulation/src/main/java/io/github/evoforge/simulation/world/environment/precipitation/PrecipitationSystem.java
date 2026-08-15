package io.github.evoforge.simulation.world.environment.precipitation;

import io.github.evoforge.simulation.world.landscape.soil.SoilLiquidSystem;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.mechanics.geometry.CellFace;
import io.github.evoforge.simulation.world.mechanics.geometry.CellSpace;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

/**
 * Applies an external Water precipitation source to an explicitly resolved exposed surface.
 *
 * <p>This system does not discover sky exposure and does not know about the liquid
 * flow solver. Terrain targets route Water through generic Soil retention first.
 * Water targets add directly to the exposed free-liquid column so rainfall over a
 * lake does not repeatedly infiltrate terrain below it. Any volume that cannot be
 * placed is returned to the caller.
 */
public final class PrecipitationSystem {

    private final TerrainLookup terrain;
    private final GeometryLookup geometry;
    private final SoilLiquidSystem soilLiquids;
    private final WaterSystem water;

    public PrecipitationSystem(
            TerrainLookup terrain,
            GeometryLookup geometry,
            SoilLiquidSystem soilLiquids,
            WaterSystem water) {

        if (terrain == null || geometry == null || soilLiquids == null || water == null) {
            throw new IllegalArgumentException(
                    "precipitation dependencies must not be null");
        }

        this.terrain = terrain;
        this.geometry = geometry;
        this.soilLiquids = soilLiquids;
        this.water = water;
    }

    public PrecipitationResult applyTerrainSurface(
            int x,
            int y,
            int terrainZ,
            int amount) {

        int input = CellVolume.requireValid(amount);
        if (!terrain.contains(x, y, terrainZ)) {
            throw new IllegalArgumentException(
                    "precipitation target must contain terrain");
        }
        if (input == CellVolume.EMPTY) {
            return emptyResult();
        }

        int infiltrated = soilLiquids.infiltrateAtMost(
                WaterSystem.TYPE,
                x,
                y,
                terrainZ,
                input);
        int remaining = input - infiltrated;
        int surfaceWater = CellVolume.EMPTY;

        Shape terrainShape = geometry.find(x, y, terrainZ);
        if (remaining > CellVolume.EMPTY && opensFromAbove(terrainShape)) {
            int added = water.addAtMost(
                    x,
                    y,
                    terrainZ,
                    remaining);
            surfaceWater += added;
            remaining -= added;
        }

        if (remaining > CellVolume.EMPTY && terrainZ < Integer.MAX_VALUE) {
            int added = water.addAtMost(
                    x,
                    y,
                    terrainZ + 1,
                    remaining);
            surfaceWater += added;
            remaining -= added;
        }

        return new PrecipitationResult(
                input,
                infiltrated,
                surfaceWater,
                remaining);
    }

    public PrecipitationResult applyWaterSurface(
            int x,
            int y,
            int waterZ,
            int amount) {

        int input = CellVolume.requireValid(amount);
        if (water.lookup().amount(x, y, waterZ) <= CellVolume.EMPTY) {
            throw new IllegalArgumentException(
                    "precipitation water target must contain water");
        }
        if (input == CellVolume.EMPTY) {
            return emptyResult();
        }

        int remaining = input;
        int surfaceWater = water.addAtMost(
                x,
                y,
                waterZ,
                remaining);
        remaining -= surfaceWater;

        if (remaining > CellVolume.EMPTY && waterZ < Integer.MAX_VALUE) {
            int addedAbove = water.addAtMost(
                    x,
                    y,
                    waterZ + 1,
                    remaining);
            surfaceWater += addedAbove;
            remaining -= addedAbove;
        }

        return new PrecipitationResult(
                input,
                CellVolume.EMPTY,
                surfaceWater,
                remaining);
    }

    private static PrecipitationResult emptyResult() {
        return new PrecipitationResult(
                CellVolume.EMPTY,
                CellVolume.EMPTY,
                CellVolume.EMPTY,
                CellVolume.EMPTY);
    }

    private static boolean opensFromAbove(Shape shape) {
        return CellSpace.boundaryOpeningFloor(
                shape,
                CellFace.POSITIVE_Z) != CellSpace.CLOSED;
    }
}
