package io.github.evoforge.simulation.world.environment.precipitation;

import io.github.evoforge.simulation.world.landscape.soil.SoilMoistureSystem;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.mechanics.geometry.CellFace;
import io.github.evoforge.simulation.world.mechanics.geometry.CellSpace;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

/**
 * Applies an external precipitation source to an explicitly exposed terrain cell.
 *
 * <p>This system does not discover sky exposure and does not know about the water
 * flow solver. Soil receives water first; remaining volume is offered to physical
 * free space at the terrain anchor when that space is open from above, then to the
 * cell directly above. Any volume that still cannot be placed is returned to the
 * caller instead of being silently destroyed.
 */
public final class PrecipitationSystem {

    private final TerrainLookup terrain;
    private final GeometryLookup geometry;
    private final SoilMoistureSystem soilMoisture;
    private final WaterSystem water;

    public PrecipitationSystem(
            TerrainLookup terrain,
            GeometryLookup geometry,
            SoilMoistureSystem soilMoisture,
            WaterSystem water) {

        if (terrain == null) {
            throw new IllegalArgumentException(
                    "terrain must not be null");
        }
        if (geometry == null) {
            throw new IllegalArgumentException(
                    "geometry must not be null");
        }
        if (soilMoisture == null) {
            throw new IllegalArgumentException(
                    "soilMoisture must not be null");
        }
        if (water == null) {
            throw new IllegalArgumentException(
                    "water must not be null");
        }

        this.terrain = terrain;
        this.geometry = geometry;
        this.soilMoisture = soilMoisture;
        this.water = water;
    }

    public PrecipitationResult applyTick(
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
            return new PrecipitationResult(
                    CellVolume.EMPTY,
                    CellVolume.EMPTY,
                    CellVolume.EMPTY,
                    CellVolume.EMPTY);
        }

        int infiltrated = soilMoisture.infiltrateAtMost(
                x,
                y,
                terrainZ,
                input);
        int remaining = input - infiltrated;
        int surfaceWater = CellVolume.EMPTY;

        Shape terrainShape = geometry.find(x, y, terrainZ);
        if (remaining > CellVolume.EMPTY
                && opensFromAbove(terrainShape)) {
            int added = water.addAtMost(
                    x,
                    y,
                    terrainZ,
                    remaining);
            surfaceWater += added;
            remaining -= added;
        }

        if (remaining > CellVolume.EMPTY
                && terrainZ < Integer.MAX_VALUE) {
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

    private static boolean opensFromAbove(
            Shape shape) {

        return CellSpace.boundaryOpeningFloor(
                shape,
                CellFace.POSITIVE_Z) != CellSpace.CLOSED;
    }
}
