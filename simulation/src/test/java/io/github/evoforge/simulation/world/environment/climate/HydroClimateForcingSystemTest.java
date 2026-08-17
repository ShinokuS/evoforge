package io.github.evoforge.simulation.world.environment.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.world.environment.atmosphere.AtmosphericWaterForcing;
import io.github.evoforge.simulation.world.environment.evaporation.EvaporationSystem;
import io.github.evoforge.simulation.world.environment.precipitation.PrecipitationSystem;
import io.github.evoforge.simulation.world.environment.precipitation.SkyPrecipitationSystem;
import io.github.evoforge.simulation.world.environment.sky.VerticalSkySurfaceSystem;
import io.github.evoforge.simulation.world.landscape.LandscapeSystem;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidTransportDefinitions;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidTransportProperties;
import io.github.evoforge.simulation.world.landscape.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.landscape.soil.SoilLiquidSystem;
import io.github.evoforge.simulation.world.landscape.soil.SoilPropertiesDefinitions;
import io.github.evoforge.simulation.world.landscape.soil.TerrainSoilPropertiesLookup;
import io.github.evoforge.simulation.world.landscape.soil.storage.SparseSoilLiquidStorage;
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

final class HydroClimateForcingSystemTest {

    @Test
    void spatialFractionalRatesRealizeExactlyFromAbsoluteTicks() {
        Fixture fixture = new Fixture();
        fixture.placeGround(0, 0);
        fixture.placeGround(1, 0);
        HydroClimateForcingSystem forcing = fixture.forcing(field(
                (x, y) -> x == 0 ? CellVolumeRate.of(500_000L, 3L) : CellVolumeRate.of(300_000L, 1L),
                (x, y) -> CellVolumeRate.ZERO));
        for (long tick = 1L; tick <= 3L; tick++) forcing.update(tick);
        assertEquals(500_000, fixture.waterAt(0, 0, 1));
        assertEquals(900_000, fixture.waterAt(1, 0, 1));
    }

    @Test
    void evaporationUsesStartOfIntervalStateBeforeFreshPrecipitationArrives() {
        Fixture fixture = new Fixture();
        fixture.placeGround(0, 0);
        HydroClimateForcingSystem forcing = fixture.forcing(field(
                (x, y) -> CellVolumeRate.of(100_000L, 1L),
                (x, y) -> CellVolumeRate.of(100_000L, 1L)));
        HydroClimateForcingResult first = forcing.update(1L);
        assertEquals(0L, first.evaporation().removed());
        assertEquals(100_000L, first.precipitation().surfaceWater());
        assertEquals(100_000, fixture.waterAt(0, 0, 1));
        HydroClimateForcingResult second = forcing.update(2L);
        assertEquals(100_000L, second.evaporation().surfaceWaterRemoved());
        assertEquals(100_000L, second.precipitation().surfaceWater());
        assertEquals(100_000, fixture.waterAt(0, 0, 1));
    }

    @Test
    void precipitationLargerThanOneCellReResolvesRisingSurface() {
        Fixture fixture = new Fixture();
        fixture.placeGround(0, 0);
        HydroClimateForcingSystem forcing = fixture.forcing(field(
                (x, y) -> CellVolumeRate.of(1_500_000L, 1L),
                (x, y) -> CellVolumeRate.ZERO));
        HydroClimateForcingResult result = forcing.update(1L);
        assertEquals(1_500_000L, result.precipitation().input());
        assertEquals(1_500_000L, result.precipitation().surfaceWater());
        assertEquals(CellVolume.FULL, fixture.waterAt(0, 0, 1));
        assertEquals(500_000, fixture.waterAt(0, 0, 2));
    }

    @Test
    void excessivePotentialEvaporationAccountsUnfulfilledTailExactly() {
        Fixture fixture = new Fixture();
        fixture.placeGround(0, 0);
        fixture.water.addAtMost(0, 0, 1, CellVolume.FULL);
        fixture.water.addAtMost(0, 0, 2, 500_000);
        HydroClimateForcingSystem forcing = fixture.forcing(field(
                (x, y) -> CellVolumeRate.ZERO,
                (x, y) -> CellVolumeRate.of(2_000_000L, 1L)));
        HydroClimateForcingResult result = forcing.update(1L);
        assertEquals(2_000_000L, result.evaporation().requested());
        assertEquals(1_500_000L, result.evaporation().surfaceWaterRemoved());
        assertEquals(500_000L, result.evaporation().unfulfilled());
        assertEquals(0, fixture.waterAt(0, 0, 1));
        assertEquals(0, fixture.waterAt(0, 0, 2));
    }

    @Test
    void zeroClimateCreatesNoHydrologyState() {
        Fixture fixture = new Fixture();
        fixture.placeGround(0, 0);
        HydroClimateForcingResult result = fixture.forcing(field(
                (x, y) -> CellVolumeRate.ZERO,
                (x, y) -> CellVolumeRate.ZERO)).update(1L);
        assertEquals(0, result.precipitation().columns());
        assertEquals(0, result.evaporation().columns());
        assertEquals(0, fixture.waterAt(0, 0, 1));
    }

    @Test
    void nonPositiveTickIsRejected() {
        Fixture fixture = new Fixture();
        HydroClimateForcingSystem forcing = fixture.forcing(field(
                (x, y) -> CellVolumeRate.ZERO,
                (x, y) -> CellVolumeRate.ZERO));
        assertThrows(IllegalArgumentException.class, () -> forcing.update(0L));
    }

    private static AtmosphericWaterForcing field(ColumnRate precipitation, ColumnRate evaporation) {
        WorldBounds bounds = new WorldBounds(-4, 4, -4, 4, -4, 8);
        return new AtmosphericWaterForcing() {
            private long currentTick;
            @Override public WorldBounds bounds() { return bounds; }
            @Override public void advanceToTick(long tick) {
                if (tick <= 0L) throw new IllegalArgumentException("tick must be positive");
                currentTick = tick;
            }
            @Override public long precipitationDueAt(int x, int y) {
                return precipitation.at(x, y).volumeDueAtTick(currentTick);
            }
            @Override public long evaporativeDemandDueAt(int x, int y) {
                return evaporation.at(x, y).volumeDueAtTick(currentTick);
            }
        };
    }

    @FunctionalInterface
    private interface ColumnRate { CellVolumeRate at(int x, int y); }

    private static final class Fixture {
        private final DefinitionRegistry<LandscapeDefinitionId> definitions = new DefinitionRegistry<>(LandscapeDefinitionId::of, LandscapeDefinitionId::asInt);
        private final LandscapeDefinitionId ground = definitions.register("test:ground");
        private final LandscapeSystem landscape = LandscapeSystem.create(new SparseTerrainStorage(), definitions);
        private final SoilPropertiesDefinitions soilProperties = new SoilPropertiesDefinitions();
        private final LiquidTransportDefinitions transport = new LiquidTransportDefinitions();
        private final SoilLiquidSystem soilLiquids;
        private final WaterSystem water;
        private final SkyPrecipitationSystem precipitation;
        private final EvaporationSystem evaporation;

        private Fixture() {
            soilProperties.freeze();
            transport.put(WaterSystem.TYPE, LiquidTransportProperties.reference());
            transport.freeze();
            soilLiquids = new SoilLiquidSystem(new SparseSoilLiquidStorage(), new TerrainSoilPropertiesLookup(landscape.terrain(), soilProperties), transport);
            LiquidSystem liquids = new LiquidSystem(new SparseLiquidStorage(), landscape.geometry());
            water = new WaterSystem(liquids);
            VerticalSkySurfaceSystem sky = new VerticalSkySurfaceSystem(landscape.terrainSurfaces(), water.surfaces());
            PrecipitationSystem precipitationPhysics = new PrecipitationSystem(landscape.terrain(), landscape.geometry(), soilLiquids, water);
            precipitation = new SkyPrecipitationSystem(sky, precipitationPhysics);
            evaporation = new EvaporationSystem(sky, water.surfaces(), soilLiquids.cells(), landscape.geometry(), water, soilLiquids);
        }

        private void placeGround(int x, int y) { landscape.placeTerrain(x, y, 0, ground); }
        private int waterAt(int x, int y, int z) { return water.lookup().amount(x, y, z); }
        private HydroClimateForcingSystem forcing(AtmosphericWaterForcing forcing) {
            return new HydroClimateForcingSystem(forcing, evaporation, precipitation);
        }
    }
}
