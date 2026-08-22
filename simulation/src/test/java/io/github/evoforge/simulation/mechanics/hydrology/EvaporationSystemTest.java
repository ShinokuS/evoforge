package io.github.evoforge.simulation.mechanics.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.world.surface.VerticalSkySurfaceSystem;
import io.github.evoforge.simulation.world.landscape.LandscapeSystem;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.liquid.LiquidTransportDefinitions;
import io.github.evoforge.simulation.world.liquid.LiquidTransportProperties;
import io.github.evoforge.simulation.world.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.soil.SoilLiquidSystem;
import io.github.evoforge.simulation.world.soil.SoilProperties;
import io.github.evoforge.simulation.world.soil.SoilPropertiesDefinitions;
import io.github.evoforge.simulation.world.soil.TerrainSoilPropertiesLookup;
import io.github.evoforge.simulation.world.soil.storage.SparseSoilLiquidStorage;
import io.github.evoforge.simulation.world.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.liquid.water.WaterSystem;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;
import io.github.evoforge.simulation.world.geometry.RampShape;

final class EvaporationSystemTest {

    @Test
    void exposedSurfaceWaterEvaporatesBeforeRetainedWater() {
        Fixture fixture = new Fixture();
        fixture.placeSoil(0, 0, 0);
        fixture.retainWater(0, 0, 0, 300_000);
        fixture.water.addAtMost(0, 0, 1, 500_000);

        EvaporationBatchResult result =
                fixture.evaporation.applyUniform(100_000);

        assertEquals(1, result.columns());
        assertEquals(100_000L, result.surfaceWaterRemoved());
        assertEquals(0L, result.retainedWaterRemoved());
        assertEquals(400_000, fixture.water.lookup().amount(0, 0, 1));
        assertEquals(300_000, fixture.retainedWater(0, 0, 0));
    }

    @Test
    void remainderContinuesIntoExposedSoilAfterPuddleDries() {
        Fixture fixture = new Fixture();
        fixture.placeSoil(0, 0, 0);
        fixture.retainWater(0, 0, 0, 200_000);
        fixture.water.addAtMost(0, 0, 1, 40_000);

        EvaporationBatchResult result =
                fixture.evaporation.applyUniform(100_000);

        assertEquals(40_000L, result.surfaceWaterRemoved());
        assertEquals(60_000L, result.retainedWaterRemoved());
        assertEquals(0L, result.unfulfilled());
        assertEquals(0, fixture.water.lookup().amount(0, 0, 1));
        assertEquals(140_000, fixture.retainedWater(0, 0, 0));
    }

    @Test
    void repeatedDemandKeepsReducingExposedRetainedWater() {
        Fixture fixture = new Fixture();
        fixture.placeSoil(0, 0, 0);
        fixture.retainWater(0, 0, 0, 70_000);
        fixture.water.addAtMost(0, 0, 1, 40_000);

        fixture.evaporation.applyUniform(50_000);
        assertEquals(0, fixture.water.lookup().amount(0, 0, 1));
        assertEquals(60_000, fixture.retainedWater(0, 0, 0));

        fixture.evaporation.applyUniform(20_000);
        assertEquals(40_000, fixture.retainedWater(0, 0, 0));

        fixture.evaporation.applyUniform(15_000);
        assertEquals(25_000, fixture.retainedWater(0, 0, 0));
    }

    @Test
    void higherTerrainShieldsWaterAndRetainedWaterBelowIt() {
        Fixture fixture = new Fixture();
        fixture.placeSoil(0, 0, 0);
        fixture.landscape.placeTerrain(0, 0, 3, fixture.rock);
        fixture.retainWater(0, 0, 0, 250_000);
        fixture.water.addAtMost(0, 0, 1, 200_000);

        EvaporationBatchResult result =
                fixture.evaporation.applyUniform(100_000);

        assertEquals(1, result.columns());
        assertEquals(0L, result.removed());
        assertEquals(100_000L, result.unfulfilled());
        assertEquals(250_000, fixture.retainedWater(0, 0, 0));
        assertEquals(200_000, fixture.water.lookup().amount(0, 0, 1));
    }

    @Test
    void waterInTopOpenRampAnchorEvaporatesBeforeRetainedWater() {
        Fixture fixture = new Fixture();
        fixture.placeSoil(0, 0, 0);
        fixture.landscape.setShape(0, 0, 0, RampShape.POSITIVE_X);
        fixture.retainWater(0, 0, 0, 200_000);
        fixture.water.addAtMost(0, 0, 0, 100_000);

        EvaporationBatchResult result =
                fixture.evaporation.applyUniform(150_000);

        assertEquals(100_000L, result.surfaceWaterRemoved());
        assertEquals(50_000L, result.retainedWaterRemoved());
        assertEquals(150_000, fixture.retainedWater(0, 0, 0));
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
        private final LandscapeSystem landscape =
                LandscapeSystem.create(
                        new SparseTerrainStorage(),
                        definitions);
        private final SoilPropertiesDefinitions soilProperties =
                new SoilPropertiesDefinitions();
        private final LiquidTransportDefinitions transport =
                new LiquidTransportDefinitions();
        private final SoilLiquidSystem soilLiquids;
        private final WaterSystem water;
        private final EvaporationSystem evaporation;

        private Fixture() {
            soilProperties.put(
                    soil,
                    new SoilProperties(CellVolume.FULL, CellVolume.FULL));
            soilProperties.freeze();
            transport.put(WaterSystem.TYPE, LiquidTransportProperties.reference());
            transport.freeze();
            soilLiquids = new SoilLiquidSystem(
                    new SparseSoilLiquidStorage(),
                    new TerrainSoilPropertiesLookup(
                            landscape.terrain(),
                            soilProperties),
                    transport);
            LiquidSystem liquids = new LiquidSystem(
                    new SparseLiquidStorage(),
                    landscape.geometry());
            water = new WaterSystem(liquids);
            VerticalSkySurfaceSystem sky = new VerticalSkySurfaceSystem(
                    landscape.terrainSurfaces(),
                    water.surfaces());
            evaporation = new EvaporationSystem(
                    sky,
                    water.surfaces(),
                    soilLiquids.cells(),
                    landscape.geometry(),
                    water,
                    soilLiquids);
        }

        private void placeSoil(int x, int y, int z) {
            landscape.placeTerrain(x, y, z, soil);
        }

        private void retainWater(int x, int y, int z, int amount) {
            assertEquals(
                    amount,
                    soilLiquids.infiltrateAtMost(
                            WaterSystem.TYPE,
                            x,
                            y,
                            z,
                            amount));
        }

        private int retainedWater(int x, int y, int z) {
            return soilLiquids.lookup().amountOf(
                    WaterSystem.TYPE,
                    x,
                    y,
                    z);
        }
    }
}
