package io.github.evoforge.simulation.world.environment.evaporation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.world.environment.sky.VerticalSkySurfaceSystem;
import io.github.evoforge.simulation.world.landscape.LandscapeSystem;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.soil.SoilHydrology;
import io.github.evoforge.simulation.world.landscape.soil.SoilHydrologyDefinitions;
import io.github.evoforge.simulation.world.landscape.soil.SoilMoistureSystem;
import io.github.evoforge.simulation.world.landscape.soil.storage.SparseSoilMoistureStorage;
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.landscape.water.storage.SparseWaterStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;

final class EvaporationSystemTest {

    @Test
    void exposedSurfaceWaterEvaporatesBeforeSoilMoisture() {
        Fixture fixture = new Fixture();
        fixture.placeSoil(0, 0, 0);
        fixture.moisture.infiltrateAtMost(0, 0, 0, 300_000);
        fixture.water.addAtMost(0, 0, 1, 500_000);

        EvaporationBatchResult result =
                fixture.evaporation.applyUniform(100_000);

        assertEquals(1, result.columns());
        assertEquals(100_000L, result.surfaceWaterRemoved());
        assertEquals(0L, result.soilMoistureRemoved());
        assertEquals(400_000, fixture.water.lookup().amount(0, 0, 1));
        assertEquals(300_000, fixture.moisture.lookup().amount(0, 0, 0));
    }

    @Test
    void remainderContinuesIntoExposedSoilAfterPuddleDries() {
        Fixture fixture = new Fixture();
        fixture.placeSoil(0, 0, 0);
        fixture.moisture.infiltrateAtMost(0, 0, 0, 200_000);
        fixture.water.addAtMost(0, 0, 1, 40_000);

        EvaporationBatchResult result =
                fixture.evaporation.applyUniform(100_000);

        assertEquals(40_000L, result.surfaceWaterRemoved());
        assertEquals(60_000L, result.soilMoistureRemoved());
        assertEquals(0L, result.unfulfilled());
        assertEquals(0, fixture.water.lookup().amount(0, 0, 1));
        assertEquals(140_000, fixture.moisture.lookup().amount(0, 0, 0));
    }

    @Test
    void higherTerrainShieldsWaterAndMoistureBelowIt() {
        Fixture fixture = new Fixture();
        fixture.placeSoil(0, 0, 0);
        fixture.landscape.placeTerrain(0, 0, 3, fixture.rock);
        fixture.moisture.infiltrateAtMost(0, 0, 0, 250_000);
        fixture.water.addAtMost(0, 0, 1, 200_000);

        EvaporationBatchResult result =
                fixture.evaporation.applyUniform(100_000);

        assertEquals(1, result.columns());
        assertEquals(0L, result.removed());
        assertEquals(100_000L, result.unfulfilled());
        assertEquals(250_000, fixture.moisture.lookup().amount(0, 0, 0));
        assertEquals(200_000, fixture.water.lookup().amount(0, 0, 1));
    }

    @Test
    void waterInTopOpenRampAnchorEvaporatesBeforeRetainedMoisture() {
        Fixture fixture = new Fixture();
        fixture.placeSoil(0, 0, 0);
        fixture.landscape.setShape(0, 0, 0, RampShape.POSITIVE_X);
        fixture.moisture.infiltrateAtMost(0, 0, 0, 200_000);
        fixture.water.addAtMost(0, 0, 0, 100_000);

        EvaporationBatchResult result =
                fixture.evaporation.applyUniform(150_000);

        assertEquals(100_000L, result.surfaceWaterRemoved());
        assertEquals(50_000L, result.soilMoistureRemoved());
        assertEquals(150_000, fixture.moisture.lookup().amount(0, 0, 0));
    }

    @Test
    void fixedAmountDependsOnSurfaceAreaNotStoredPercentage() {
        Fixture fixture = new Fixture();
        fixture.landscape.placeTerrain(0, 0, 0, fixture.rock);
        fixture.landscape.placeTerrain(1, 0, 0, fixture.rock);
        fixture.water.addAtMost(0, 0, 1, 100_000);
        fixture.water.addAtMost(1, 0, 1, CellVolume.FULL);

        EvaporationBatchResult result =
                fixture.evaporation.applyUniform(80_000);

        assertEquals(2, result.columns());
        assertEquals(160_000L, result.surfaceWaterRemoved());
        assertEquals(20_000, fixture.water.lookup().amount(0, 0, 1));
        assertEquals(920_000, fixture.water.lookup().amount(1, 0, 1));
    }

    private static final class Fixture {
        private final DefinitionRegistry<LandscapeDefinitionId> definitions =
                new DefinitionRegistry<>(
                        LandscapeDefinitionId::of,
                        LandscapeDefinitionId::asInt);
        private final LandscapeDefinitionId soil =
                definitions.register("test:soil");
        private final LandscapeDefinitionId rock =
                definitions.register("test:rock");
        private final SoilHydrologyDefinitions hydrology =
                new SoilHydrologyDefinitions();
        private final LandscapeSystem landscape =
                LandscapeSystem.create(
                        new SparseTerrainStorage(),
                        definitions);
        private final SoilMoistureSystem moisture;
        private final WaterSystem water;
        private final EvaporationSystem evaporation;

        private Fixture() {
            hydrology.put(
                    soil,
                    new SoilHydrology(CellVolume.FULL, CellVolume.FULL));
            hydrology.freeze();
            moisture = new SoilMoistureSystem(
                    new SparseSoilMoistureStorage(),
                    landscape.terrain(),
                    hydrology);
            water = new WaterSystem(
                    new SparseWaterStorage(),
                    landscape.geometry());
            VerticalSkySurfaceSystem sky = new VerticalSkySurfaceSystem(
                    landscape.terrainSurfaces(),
                    water.surfaces());
            evaporation = new EvaporationSystem(
                    sky,
                    water.surfaces(),
                    moisture.cells(),
                    landscape.geometry(),
                    water,
                    moisture);
        }

        private void placeSoil(int x, int y, int z) {
            landscape.placeTerrain(x, y, z, soil);
        }
    }
}
