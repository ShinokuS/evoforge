package io.github.evoforge.simulation.world.landscape.water;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidTypeId;
import io.github.evoforge.simulation.world.landscape.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.landscape.soil.SoilHydrology;
import io.github.evoforge.simulation.world.landscape.soil.SoilHydrologyDefinitions;
import io.github.evoforge.simulation.world.landscape.soil.SoilLiquidSystem;
import io.github.evoforge.simulation.world.landscape.soil.SoilMoistureSystem;
import io.github.evoforge.simulation.world.landscape.soil.TerrainSoilHydrologyLookup;
import io.github.evoforge.simulation.world.landscape.soil.storage.SparseSoilLiquidStorage;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;

final class WaterSoilExchangeSystemTest {

    private static final LandscapeDefinitionId SOIL =
            LandscapeDefinitionId.of(0);

    @Test
    void runOnWaterInfiltratesSupportingDryTerrain() {
        Fixture fixture = fixture(100_000, 30_000);
        fixture.water.addAtMost(0, 0, 0, 20_000);

        assertEquals(20_000L, fixture.exchange.update());
        assertEquals(0, fixture.water.lookup().amount(0, 0, 0));
        assertEquals(20_000, fixture.soil.lookup().amount(0, 0, -1));
        assertEquals(20_000, totalWater(fixture));
    }

    @Test
    void saturatedSoilLeavesIncomingWaterFree() {
        Fixture fixture = fixture(10_000, 10_000);
        assertEquals(
                10_000,
                fixture.soil.infiltrateAtMost(0, 0, -1, 10_000));
        fixture.water.addAtMost(0, 0, 0, 7_000);

        assertEquals(0L, fixture.exchange.update());
        assertEquals(7_000, fixture.water.lookup().amount(0, 0, 0));
        assertEquals(10_000, fixture.soil.lookup().amount(0, 0, -1));
        assertEquals(17_000, totalWater(fixture));
    }

    @Test
    void infiltrationLimitTransfersOnlyBoundedAmountPerActiveStep() {
        Fixture fixture = fixture(100_000, 3_000);
        fixture.water.addAtMost(0, 0, 0, 10_000);

        assertEquals(3_000L, fixture.exchange.update());
        assertEquals(7_000, fixture.water.lookup().amount(0, 0, 0));
        assertEquals(3_000, fixture.soil.lookup().amount(0, 0, -1));
        assertEquals(10_000, totalWater(fixture));
    }

    @Test
    void nonWaterLiquidUsesTheSameGenericSoilInfiltrationMechanic() {
        Fixture fixture = fixture(100_000, 30_000);
        LiquidTypeId blood = LiquidTypeId.of("blood");
        fixture.liquids.addAtMost(blood, 0, 0, 0, 20_000);

        assertEquals(20_000L, fixture.exchange.update());
        assertEquals(0, fixture.liquids.lookup().amountOf(blood, 0, 0, 0));
        assertEquals(
                20_000,
                fixture.retained.lookup().amountOf(blood, 0, 0, -1));
        assertEquals(0, fixture.soil.lookup().amount(0, 0, -1));
    }

    private static Fixture fixture(
            int capacity,
            int infiltrationLimit) {

        SoilHydrologyDefinitions definitions =
                new SoilHydrologyDefinitions();
        definitions.put(
                SOIL,
                new SoilHydrology(capacity, infiltrationLimit));

        TerrainLookup terrain = (x, y, z) ->
                x == 0 && y == 0 && z == -1
                        ? SOIL
                        : null;
        TerrainSoilHydrologyLookup hydrology =
                new TerrainSoilHydrologyLookup(
                        terrain,
                        definitions);
        SoilLiquidSystem retained = new SoilLiquidSystem(
                new SparseSoilLiquidStorage(),
                hydrology);
        SoilMoistureSystem soil = new SoilMoistureSystem(
                retained,
                WaterSystem.TYPE);
        GeometryLookup geometry = (x, y, z) -> null;
        LiquidSystem liquids = new LiquidSystem(
                new SparseLiquidStorage(),
                geometry);
        WaterSystem water = new WaterSystem(liquids);
        WaterSoilExchangeSystem exchange =
                new WaterSoilExchangeSystem(
                        water,
                        terrain,
                        soil);
        return new Fixture(liquids, water, retained, soil, exchange);
    }

    private static int totalWater(Fixture fixture) {
        return fixture.water.lookup().amount(0, 0, 0)
                + fixture.soil.lookup().amount(0, 0, -1);
    }

    private record Fixture(
            LiquidSystem liquids,
            WaterSystem water,
            SoilLiquidSystem retained,
            SoilMoistureSystem soil,
            WaterSoilExchangeSystem exchange) {
    }
}
