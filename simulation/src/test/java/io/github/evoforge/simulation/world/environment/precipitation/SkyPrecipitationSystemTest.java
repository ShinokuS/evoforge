package io.github.evoforge.simulation.world.environment.precipitation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.world.environment.sky.VerticalSkySurfaceSystem;
import io.github.evoforge.simulation.world.landscape.LandscapeSystem;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidTransportDefinitions;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidTransportProperties;
import io.github.evoforge.simulation.world.landscape.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.landscape.soil.SoilLiquidSystem;
import io.github.evoforge.simulation.world.landscape.soil.SoilProperties;
import io.github.evoforge.simulation.world.landscape.soil.SoilPropertiesDefinitions;
import io.github.evoforge.simulation.world.landscape.soil.TerrainSoilPropertiesLookup;
import io.github.evoforge.simulation.world.landscape.soil.storage.SparseSoilLiquidStorage;
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;

final class SkyPrecipitationSystemTest {

    @Test
    void highestTerrainRoofReceivesRainInsteadOfCoveredSoil() {
        Fixture fixture = new Fixture();
        fixture.soilProperties.put(
                fixture.soil,
                new SoilProperties(800_000, 800_000));
        fixture.landscape.placeTerrain(0, 0, 0, fixture.soil);
        fixture.landscape.placeTerrain(0, 0, 3, fixture.roof);

        PrecipitationBatchResult result =
                fixture.sky().applyUniform(120_000);

        assertEquals(1, result.columns());
        assertEquals(120_000L, result.input());
        assertEquals(0L, result.infiltrated());
        assertEquals(120_000L, result.surfaceWater());
        assertEquals(0, fixture.retainedWater(0, 0, 0));
        assertEquals(120_000, fixture.water.lookup().amount(0, 0, 4));
    }

    @Test
    void exposedWaterAboveTerrainReceivesRainWithoutSoilInfiltration() {
        Fixture fixture = new Fixture();
        fixture.soilProperties.put(
                fixture.soil,
                new SoilProperties(800_000, 800_000));
        fixture.landscape.placeTerrain(2, 5, 0, fixture.soil);
        fixture.water.addAtMost(2, 5, 1, 400_000);

        PrecipitationBatchResult result =
                fixture.sky().applyUniform(150_000);

        assertEquals(1, result.columns());
        assertEquals(0L, result.infiltrated());
        assertEquals(150_000L, result.surfaceWater());
        assertEquals(0, fixture.retainedWater(2, 5, 0));
        assertEquals(550_000, fixture.water.lookup().amount(2, 5, 1));
    }

    @Test
    void exposedWaterWithoutTerrainStillReceivesRain() {
        Fixture fixture = new Fixture();
        fixture.water.addAtMost(9, -2, 5, 100_000);

        PrecipitationBatchResult result =
                fixture.sky().applyUniform(20_000);

        assertEquals(1, result.columns());
        assertEquals(20_000L, result.input());
        assertEquals(0L, result.infiltrated());
        assertEquals(20_000L, result.surfaceWater());
        assertEquals(0L, result.unplaced());
        assertEquals(120_000, fixture.water.lookup().amount(9, -2, 5));
    }

    @Test
    void terrainSurfaceStillInfiltratesBeforeCreatingFreeWater() {
        Fixture fixture = new Fixture();
        fixture.soilProperties.put(
                fixture.soil,
                new SoilProperties(500_000, 100_000));
        fixture.landscape.placeTerrain(-4, 1, 7, fixture.soil);

        PrecipitationBatchResult result =
                fixture.sky().applyUniform(250_000);

        assertEquals(100_000L, result.infiltrated());
        assertEquals(150_000L, result.surfaceWater());
        assertEquals(100_000, fixture.retainedWater(-4, 1, 7));
        assertEquals(150_000, fixture.water.lookup().amount(-4, 1, 8));
    }

    @Test
    void batchAccountingCoversUnionWithoutDoubleCountingSharedColumns() {
        Fixture fixture = new Fixture();
        fixture.landscape.placeTerrain(0, 0, 0, fixture.roof);
        fixture.landscape.placeTerrain(1, 0, 2, fixture.roof);
        fixture.landscape.placeTerrain(1, 0, 5, fixture.roof);
        fixture.landscape.placeTerrain(-1, 3, -2, fixture.roof);
        fixture.water.addAtMost(0, 0, 1, 100_000);
        fixture.water.addAtMost(8, 8, 4, 100_000);

        PrecipitationBatchResult result =
                fixture.sky().applyUniform(10_000);

        assertEquals(4, result.columns());
        assertEquals(40_000L, result.input());
        assertEquals(
                result.input(),
                result.infiltrated()
                        + result.surfaceWater()
                        + result.unplaced());
    }

    private static final class Fixture {
        private final DefinitionRegistry<LandscapeDefinitionId> definitions =
                new DefinitionRegistry<>(
                        LandscapeDefinitionId::of,
                        LandscapeDefinitionId::asInt);
        private final LandscapeDefinitionId soil =
                definitions.register("test:soil");
        private final LandscapeDefinitionId roof =
                definitions.register("test:roof");
        private final SoilPropertiesDefinitions soilProperties =
                new SoilPropertiesDefinitions();
        private final LandscapeSystem landscape =
                LandscapeSystem.create(
                        new SparseTerrainStorage(),
                        definitions);
        private final LiquidTransportDefinitions transport =
                referenceWaterTransport();
        private final SoilLiquidSystem retained =
                new SoilLiquidSystem(
                        new SparseSoilLiquidStorage(),
                        new TerrainSoilPropertiesLookup(
                                landscape.terrain(),
                                soilProperties),
                        transport);
        private final LiquidSystem liquids = new LiquidSystem(
                new SparseLiquidStorage(),
                landscape.geometry());
        private final WaterSystem water = new WaterSystem(liquids);
        private final PrecipitationSystem precipitation =
                new PrecipitationSystem(
                        landscape.terrain(),
                        landscape.geometry(),
                        retained,
                        water);

        private SkyPrecipitationSystem sky() {
            return new SkyPrecipitationSystem(
                    new VerticalSkySurfaceSystem(
                            landscape.terrainSurfaces(),
                            water.surfaces()),
                    precipitation);
        }

        private int retainedWater(int x, int y, int z) {
            return retained.lookup().amountOf(
                    WaterSystem.TYPE,
                    x,
                    y,
                    z);
        }
    }

    private static LiquidTransportDefinitions referenceWaterTransport() {
        LiquidTransportDefinitions transport = new LiquidTransportDefinitions();
        transport.put(WaterSystem.TYPE, LiquidTransportProperties.reference());
        return transport;
    }
}
