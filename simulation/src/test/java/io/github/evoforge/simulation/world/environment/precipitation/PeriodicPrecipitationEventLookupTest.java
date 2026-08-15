package io.github.evoforge.simulation.world.environment.precipitation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.time.SimulationTime;
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

final class PeriodicPrecipitationEventLookupTest {

    @Test
    void reportsBothUpcomingAndJustCompletedEventTick() {
        MutableTime time = new MutableTime();
        PeriodicPrecipitationSystem periodic = periodic(
                new PrecipitationSchedule(10_000, 5L),
                time);
        periodic.bindScheduler((delayTicks, processId) -> { });

        periodic.start();
        assertFalse(periodic.occursAt(4L));
        assertTrue(periodic.occursAt(5L));

        time.tick = 5L;
        periodic.resume(0L);

        assertTrue(periodic.occursAt(5L));
        assertTrue(periodic.occursAt(10L));
        assertFalse(periodic.occursAt(6L));
    }

    @Test
    void cyclicRainRunsAsSmallPulsesThenSleepsAcrossTheDryWindow() {
        MutableTime time = new MutableTime();
        PrecipitationSchedule schedule = PrecipitationSchedule.cyclic(
                25,
                1L,
                3L,
                10L);
        PeriodicPrecipitationSystem periodic = periodic(schedule, time);
        long[] lastDelay = {-1L};
        periodic.bindScheduler((delayTicks, processId) -> lastDelay[0] = delayTicks);

        periodic.start();
        assertEquals(1L, periodic.nextEvaluationTick());
        assertEquals(1L, lastDelay[0]);
        assertTrue(schedule.activeAt(1L));
        assertTrue(schedule.activeAt(3L));
        assertFalse(schedule.activeAt(4L));
        assertFalse(schedule.activeAt(10L));
        assertTrue(schedule.activeAt(11L));

        time.tick = 1L;
        periodic.resume(0L);
        assertEquals(2L, periodic.nextEvaluationTick());

        time.tick = 2L;
        periodic.resume(0L);
        assertEquals(3L, periodic.nextEvaluationTick());

        time.tick = 3L;
        periodic.resume(0L);

        assertEquals(11L, periodic.nextEvaluationTick());
        assertEquals(8L, lastDelay[0]);
        assertTrue(periodic.occursAt(3L));
        assertTrue(periodic.occursAt(11L));
        assertFalse(periodic.occursAt(4L));
    }

    private static PeriodicPrecipitationSystem periodic(
            PrecipitationSchedule schedule,
            MutableTime time) {
        DefinitionRegistry<LandscapeDefinitionId> definitions =
                new DefinitionRegistry<>(
                        LandscapeDefinitionId::of,
                        LandscapeDefinitionId::asInt);
        LandscapeSystem landscape = LandscapeSystem.create(
                new SparseTerrainStorage(),
                definitions);
        LiquidTransportDefinitions transport = new LiquidTransportDefinitions();
        transport.put(WaterSystem.TYPE, LiquidTransportProperties.reference());
        SoilLiquidSystem retained = new SoilLiquidSystem(
                new SparseSoilLiquidStorage(),
                new TerrainSoilPropertiesLookup(
                        landscape.terrain(),
                        new SoilPropertiesDefinitions()),
                transport);
        LiquidSystem liquids = new LiquidSystem(
                new SparseLiquidStorage(),
                landscape.geometry());
        WaterSystem water = new WaterSystem(liquids);
        PrecipitationSystem precipitation = new PrecipitationSystem(
                landscape.terrain(),
                landscape.geometry(),
                retained,
                water);
        SkyPrecipitationSystem sky = new SkyPrecipitationSystem(
                new VerticalSkySurfaceSystem(
                        landscape.terrainSurfaces(),
                        water.surfaces()),
                precipitation);
        return new PeriodicPrecipitationSystem(
                sky,
                schedule,
                time);
    }

    private static final class MutableTime implements SimulationTime {
        private long tick;

        @Override
        public long tick() {
            return tick;
        }
    }
}
