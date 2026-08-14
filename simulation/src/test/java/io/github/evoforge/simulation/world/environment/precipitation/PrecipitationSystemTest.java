package io.github.evoforge.simulation.world.environment.precipitation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.soil.SoilHydrology;
import io.github.evoforge.simulation.world.landscape.soil.SoilHydrologyDefinitions;
import io.github.evoforge.simulation.world.landscape.soil.SoilMoistureSystem;
import io.github.evoforge.simulation.world.landscape.soil.storage.SparseSoilMoistureStorage;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.landscape.water.storage.SparseWaterStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.FullShape;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionPorts;

final class PrecipitationSystemTest {

    private static final LandscapeDefinitionId TERRAIN_ID =
            LandscapeDefinitionId.of(0);

    @Test
    void soilAbsorbsBeforeExcessBecomesSurfaceWater() {
        Fixture fixture = fixture(
                new SoilHydrology(500_000, 200_000),
                fullTerrainGeometry());

        PrecipitationResult result = fixture.precipitation.applyTerrainSurface(
                0, 0, 0, 300_000);

        assertEquals(
                new PrecipitationResult(
                        300_000,
                        200_000,
                        100_000,
                        0),
                result);
        assertEquals(
                200_000,
                fixture.soil.lookup().amount(0, 0, 0));
        assertEquals(
                100_000,
                fixture.water.lookup().amount(0, 0, 1));
    }

    @Test
    void moistureCapacityBoundsLaterInfiltration() {
        Fixture fixture = fixture(
                new SoilHydrology(250_000, 200_000),
                fullTerrainGeometry());

        fixture.precipitation.applyTerrainSurface(0, 0, 0, 200_000);
        PrecipitationResult result = fixture.precipitation.applyTerrainSurface(
                0, 0, 0, 200_000);

        assertEquals(50_000, result.infiltrated());
        assertEquals(150_000, result.surfaceWater());
        assertEquals(250_000, fixture.soil.lookup().amount(0, 0, 0));
        assertEquals(150_000, fixture.water.lookup().amount(0, 0, 1));
    }

    @Test
    void missingSoilAspectMeansNonAbsorbingTerrain() {
        Fixture fixture = fixture(
                null,
                fullTerrainGeometry());

        PrecipitationResult result = fixture.precipitation.applyTerrainSurface(
                0, 0, 0, 180_000);

        assertEquals(0, result.infiltrated());
        assertEquals(180_000, result.surfaceWater());
        assertEquals(0, fixture.soil.lookup().amount(0, 0, 0));
        assertEquals(180_000, fixture.water.lookup().amount(0, 0, 1));
    }

    @Test
    void rampReceivesWaterInItsOpenAnchorSpaceBeforeCellAbove() {
        GeometryLookup geometry = (x, y, z) ->
                z == 0 ? RampShape.POSITIVE_X : null;
        Fixture fixture = fixture(null, geometry);

        PrecipitationResult result = fixture.precipitation.applyTerrainSurface(
                0, 0, 0, CellVolume.FULL);

        assertEquals(CellVolume.FULL, result.surfaceWater());
        assertEquals(0, result.unplaced());
        assertEquals(
                CellVolume.FULL / 2,
                fixture.water.lookup().amount(0, 0, 0));
        assertEquals(
                CellVolume.FULL / 2,
                fixture.water.lookup().amount(0, 0, 1));
    }

    @Test
    void unknownShapeWithClosedTopDoesNotReceiveRainIntoInternalFreeSpace() {
        Shape closedHalfCell = new Shape() {
            @Override
            public int solidVolume() {
                return CellVolume.FULL / 2;
            }

            @Override
            public long transitionPorts(
                    int relativeX,
                    int relativeY,
                    int relativeZ) {

                return TransitionPorts.NONE;
            }
        };
        GeometryLookup geometry = (x, y, z) ->
                z == 0 ? closedHalfCell : null;
        Fixture fixture = fixture(null, geometry);

        fixture.precipitation.applyTerrainSurface(0, 0, 0, 400_000);

        assertEquals(0, fixture.water.lookup().amount(0, 0, 0));
        assertEquals(400_000, fixture.water.lookup().amount(0, 0, 1));
    }

    @Test
    void blockedSurfaceReturnsUnplacedVolumeInsteadOfDestroyingIt() {
        GeometryLookup geometry = (x, y, z) ->
                z == 0 || z == 1 ? FullShape.INSTANCE : null;
        Fixture fixture = fixture(null, geometry);

        PrecipitationResult result = fixture.precipitation.applyTerrainSurface(
                0, 0, 0, 300_000);

        assertEquals(
                new PrecipitationResult(
                        300_000,
                        0,
                        0,
                        300_000),
                result);
    }

    @Test
    void exposedWaterTargetBypassesSoilAndCanGrowIntoCellAbove() {
        Fixture fixture = fixture(
                new SoilHydrology(CellVolume.FULL, CellVolume.FULL),
                fullTerrainGeometry());
        fixture.water.addAtMost(0, 0, 1, 900_000);

        PrecipitationResult result = fixture.precipitation.applyWaterSurface(
                0, 0, 1, 250_000);

        assertEquals(0, result.infiltrated());
        assertEquals(250_000, result.surfaceWater());
        assertEquals(0, fixture.soil.lookup().amount(0, 0, 0));
        assertEquals(CellVolume.FULL, fixture.water.lookup().amount(0, 0, 1));
        assertEquals(150_000, fixture.water.lookup().amount(0, 0, 2));
    }

    @Test
    void precipitationRequiresExplicitExistingTargets() {
        SoilHydrologyDefinitions definitions = new SoilHydrologyDefinitions();
        TerrainLookup terrain = (x, y, z) -> null;
        GeometryLookup geometry = (x, y, z) -> null;
        SoilMoistureSystem soil = new SoilMoistureSystem(
                new SparseSoilMoistureStorage(),
                terrain,
                definitions);
        WaterSystem water = new WaterSystem(
                new SparseWaterStorage(),
                geometry);
        PrecipitationSystem precipitation = new PrecipitationSystem(
                terrain,
                geometry,
                soil,
                water);

        assertThrows(
                IllegalArgumentException.class,
                () -> precipitation.applyTerrainSurface(0, 0, 0, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> precipitation.applyWaterSurface(0, 0, 0, 1));
    }

    private static Fixture fixture(
            SoilHydrology hydrology,
            GeometryLookup geometry) {

        SoilHydrologyDefinitions definitions = new SoilHydrologyDefinitions();
        if (hydrology != null) {
            definitions.put(TERRAIN_ID, hydrology);
        }
        definitions.freeze();

        TerrainLookup terrain = (x, y, z) ->
                z == 0 ? TERRAIN_ID : null;
        SoilMoistureSystem soil = new SoilMoistureSystem(
                new SparseSoilMoistureStorage(),
                terrain,
                definitions);
        WaterSystem water = new WaterSystem(
                new SparseWaterStorage(),
                geometry);

        return new Fixture(
                soil,
                water,
                new PrecipitationSystem(
                        terrain,
                        geometry,
                        soil,
                        water));
    }

    private static GeometryLookup fullTerrainGeometry() {
        return (x, y, z) ->
                z == 0 ? FullShape.INSTANCE : null;
    }

    private record Fixture(
            SoilMoistureSystem soil,
            WaterSystem water,
            PrecipitationSystem precipitation) {
    }
}
