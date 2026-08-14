package io.github.evoforge.simulation.world.environment.evaporation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.time.SimulationTime;
import io.github.evoforge.simulation.world.environment.precipitation.PrecipitationEventLookup;
import io.github.evoforge.simulation.world.environment.sky.VerticalSkySurfaceSystem;
import io.github.evoforge.simulation.world.landscape.LandscapeSystem;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.soil.SoilHydrologyDefinitions;
import io.github.evoforge.simulation.world.landscape.soil.SoilMoistureSystem;
import io.github.evoforge.simulation.world.landscape.soil.storage.SparseSoilMoistureStorage;
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.landscape.water.storage.SparseWaterStorage;

final class PeriodicEvaporationSystemTest {

    @Test
    void schedulesRepeatedFiniteEvaporation() {
        Fixture fixture = new Fixture(tick -> false);
        fixture.landscape.placeTerrain(0, 0, 0, fixture.terrainId);
        fixture.water.addAtMost(0, 0, 1, 300_000);

        fixture.periodic.start();
        assertTrue(fixture.periodic.scheduled());
        assertEquals(5L, fixture.periodic.nextEvaluationTick());
        assertEquals(5L, fixture.scheduler.lastDelay);

        fixture.time.tick = 5L;
        fixture.periodic.resume(0L);

        assertFalse(fixture.periodic.lastSuppressed());
        assertEquals(100_000L, fixture.periodic.lastResult().surfaceWaterRemoved());
        assertEquals(200_000, fixture.water.lookup().amount(0, 0, 1));
        assertEquals(10L, fixture.periodic.nextEvaluationTick());
    }

    @Test
    void precipitationOnSameTickSuppressesEvaporation() {
        Fixture fixture = new Fixture(tick -> tick == 5L);
        fixture.landscape.placeTerrain(0, 0, 0, fixture.terrainId);
        fixture.water.addAtMost(0, 0, 1, 300_000);

        fixture.periodic.start();
        fixture.time.tick = 5L;
        fixture.periodic.resume(0L);

        assertTrue(fixture.periodic.lastSuppressed());
        assertEquals(EvaporationBatchResult.empty(), fixture.periodic.lastResult());
        assertEquals(300_000, fixture.water.lookup().amount(0, 0, 1));
        assertEquals(10L, fixture.periodic.nextEvaluationTick());
    }

    private static final class Fixture {
        private final DefinitionRegistry<LandscapeDefinitionId> definitions =
                new DefinitionRegistry<>(
                        LandscapeDefinitionId::of,
                        LandscapeDefinitionId::asInt);
        private final LandscapeDefinitionId terrainId =
                definitions.register("test:terrain");
        private final LandscapeSystem landscape =
                LandscapeSystem.create(
                        new SparseTerrainStorage(),
                        definitions);
        private final SoilHydrologyDefinitions soilHydrology =
                new SoilHydrologyDefinitions();
        private final SoilMoistureSystem moisture =
                new SoilMoistureSystem(
                        new SparseSoilMoistureStorage(),
                        landscape.terrain(),
                        soilHydrology);
        private final WaterSystem water = new WaterSystem(
                new SparseWaterStorage(),
                landscape.geometry());
        private final MutableTime time = new MutableTime();
        private final RecordingScheduler scheduler = new RecordingScheduler();
        private final PeriodicEvaporationSystem periodic;

        private Fixture(PrecipitationEventLookup precipitation) {
            VerticalSkySurfaceSystem sky = new VerticalSkySurfaceSystem(
                    landscape.terrainSurfaces(),
                    water.surfaces());
            EvaporationSystem evaporation = new EvaporationSystem(
                    sky,
                    water.surfaces(),
                    moisture.cells(),
                    landscape.geometry(),
                    water,
                    moisture);
            periodic = new PeriodicEvaporationSystem(
                    evaporation,
                    new EvaporationSchedule(100_000, 5L),
                    time,
                    precipitation);
            periodic.bindScheduler(scheduler);
        }
    }

    private static final class MutableTime implements SimulationTime {
        private long tick;

        @Override
        public long tick() {
            return tick;
        }
    }

    private static final class RecordingScheduler implements ProcessScheduler {
        private long lastDelay = -1L;
        private long lastProcessId = -1L;

        @Override
        public void scheduleAfter(long delayTicks, long processId) {
            lastDelay = delayTicks;
            lastProcessId = processId;
            assertEquals(0L, lastProcessId);
        }
    }
}
