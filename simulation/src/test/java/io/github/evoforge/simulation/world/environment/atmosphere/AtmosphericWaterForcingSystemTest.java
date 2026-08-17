package io.github.evoforge.simulation.world.environment.atmosphere;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
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
import org.junit.jupiter.api.Test;

final class AtmosphericWaterForcingSystemTest {

    @Test
    void fractionalSpatialRatesRealizeExactly() {
        Fixture f = new Fixture();
        f.ground(0, 0);
        f.ground(1, 0);
        AtmosphericWaterForcingSystem system = f.system(field(
                (x, y) -> x == 0 ? CellVolumeRate.of(500_000L, 3L) : CellVolumeRate.of(300_000L, 1L),
                (x, y) -> CellVolumeRate.ZERO));
        for (long tick = 1; tick <= 3; tick++) system.update(tick);
        assertEquals(500_000, f.water(0, 0, 1));
        assertEquals(900_000, f.water(1, 0, 1));
    }

    @Test
    void evaporationUsesStartOfIntervalBeforeFreshRain() {
        Fixture f = new Fixture();
        f.ground(0, 0);
        AtmosphericWaterForcingSystem system = f.system(field(
                (x, y) -> CellVolumeRate.of(100_000L, 1L),
                (x, y) -> CellVolumeRate.of(100_000L, 1L)));
        AtmosphericWaterForcingResult first = system.update(1L);
        assertEquals(0L, first.evaporation().removed());
        assertEquals(100_000L, first.precipitation().surfaceWater());
        AtmosphericWaterForcingResult second = system.update(2L);
        assertEquals(100_000L, second.evaporation().surfaceWaterRemoved());
        assertEquals(100_000L, second.precipitation().surfaceWater());
        assertEquals(100_000, f.water(0, 0, 1));
    }

    @Test
    void precipitationCanFillMoreThanOneVerticalCell() {
        Fixture f = new Fixture();
        f.ground(0, 0);
        AtmosphericWaterForcingResult result = f.system(field(
                (x, y) -> CellVolumeRate.of(1_500_000L, 1L),
                (x, y) -> CellVolumeRate.ZERO)).update(1L);
        assertEquals(1_500_000L, result.precipitation().surfaceWater());
        assertEquals(CellVolume.FULL, f.water(0, 0, 1));
        assertEquals(500_000, f.water(0, 0, 2));
    }

    @Test
    void excessiveEvaporationReportsUnfulfilledTail() {
        Fixture f = new Fixture();
        f.ground(0, 0);
        f.water.addAtMost(0, 0, 1, CellVolume.FULL);
        f.water.addAtMost(0, 0, 2, 500_000);
        AtmosphericWaterForcingResult result = f.system(field(
                (x, y) -> CellVolumeRate.ZERO,
                (x, y) -> CellVolumeRate.of(2_000_000L, 1L))).update(1L);
        assertEquals(1_500_000L, result.evaporation().surfaceWaterRemoved());
        assertEquals(500_000L, result.evaporation().unfulfilled());
    }

    @Test
    void nonPositiveTickIsRejected() {
        Fixture f = new Fixture();
        AtmosphericWaterForcingSystem system = f.system(field(
                (x, y) -> CellVolumeRate.ZERO,
                (x, y) -> CellVolumeRate.ZERO));
        assertThrows(IllegalArgumentException.class, () -> system.update(0L));
    }

    private static AtmosphericWaterForcing field(ColumnRate precipitation, ColumnRate evaporation) {
        WorldBounds bounds = new WorldBounds(-4, 4, -4, 4, -4, 8);
        return new AtmosphericWaterForcing() {
            private long tick;
            @Override public WorldBounds bounds() { return bounds; }
            @Override public void advanceToTick(long value) {
                if (value <= 0L) throw new IllegalArgumentException("tick must be positive");
                tick = value;
            }
            @Override public long precipitationDueAt(int x, int y) {
                return precipitation.at(x, y).volumeDueAtTick(tick);
            }
            @Override public long evaporativeDemandDueAt(int x, int y) {
                return evaporation.at(x, y).volumeDueAtTick(tick);
            }
        };
    }

    @FunctionalInterface
    private interface ColumnRate { CellVolumeRate at(int x, int y); }

    private static final class Fixture {
        private final DefinitionRegistry<LandscapeDefinitionId> definitions =
                new DefinitionRegistry<>(LandscapeDefinitionId::of, LandscapeDefinitionId::asInt);
        private final LandscapeDefinitionId ground = definitions.register("test:ground");
        private final LandscapeSystem landscape = LandscapeSystem.create(
                new SparseTerrainStorage(), definitions);
        private final SoilPropertiesDefinitions soilProperties = new SoilPropertiesDefinitions();
        private final LiquidTransportDefinitions transport = new LiquidTransportDefinitions();
        private final SoilLiquidSystem soil;
        private final WaterSystem water;
        private final SkyPrecipitationSystem precipitation;
        private final EvaporationSystem evaporation;

        private Fixture() {
            soilProperties.freeze();
            transport.put(WaterSystem.TYPE, LiquidTransportProperties.reference());
            transport.freeze();
            soil = new SoilLiquidSystem(
                    new SparseSoilLiquidStorage(),
                    new TerrainSoilPropertiesLookup(landscape.terrain(), soilProperties),
                    transport);
            LiquidSystem liquids = new LiquidSystem(new SparseLiquidStorage(), landscape.geometry());
            water = new WaterSystem(liquids);
            VerticalSkySurfaceSystem sky = new VerticalSkySurfaceSystem(
                    landscape.terrainSurfaces(), water.surfaces());
            precipitation = new SkyPrecipitationSystem(
                    sky,
                    new PrecipitationSystem(landscape.terrain(), landscape.geometry(), soil, water));
            evaporation = new EvaporationSystem(
                    sky, water.surfaces(), soil.cells(), landscape.geometry(), water, soil);
        }

        void ground(int x, int y) { landscape.placeTerrain(x, y, 0, ground); }
        int water(int x, int y, int z) { return water.lookup().amount(x, y, z); }
        AtmosphericWaterForcingSystem system(AtmosphericWaterForcing forcing) {
            return new AtmosphericWaterForcingSystem(forcing, evaporation, precipitation);
        }
    }
}
