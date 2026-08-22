package io.github.evoforge.simulation.mechanics.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.kernel.scheduling.ProcessScheduler;
import io.github.evoforge.simulation.kernel.time.SimulationTime;
import io.github.evoforge.simulation.mechanics.hydrology.PrecipitationEventLookup;
import io.github.evoforge.simulation.world.surface.VerticalSkySurfaceSystem;
import io.github.evoforge.simulation.world.landscape.LandscapeSystem;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.liquid.LiquidTransportDefinitions;
import io.github.evoforge.simulation.world.liquid.LiquidTransportProperties;
import io.github.evoforge.simulation.world.liquid.storage.SparseLiquidStorage;
import io.github.evoforge.simulation.world.soil.SoilLiquidSystem;
import io.github.evoforge.simulation.world.soil.SoilPropertiesDefinitions;
import io.github.evoforge.simulation.world.soil.TerrainSoilPropertiesLookup;
import io.github.evoforge.simulation.world.soil.storage.SparseSoilLiquidStorage;
import io.github.evoforge.simulation.world.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.liquid.water.WaterSystem;

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
        private final LiquidTransportDefinitions transport =
                referenceWaterTransport();
        private final SoilLiquidSystem soilLiquids =
                new SoilLiquidSystem(
                        new SparseSoilLiquidStorage(),
                        new TerrainSoilPropertiesLookup(
                                landscape.terrain(),
                                new SoilPropertiesDefinitions()),
                        transport);
        private final LiquidSystem liquids = new LiquidSystem(
                new SparseLiquidStorage(),
                landscape.geometry());
        private final WaterSystem water = new WaterSystem(liquids);
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
                    soilLiquids.cells(),
                    landscape.geometry(),
                    water,
                    soilLiquids);
            periodic = new PeriodicEvaporationSystem(
                    evaporation,
                    new EvaporationSchedule(100_000, 5L),
                    time,
                    precipitation);
            periodic.bindScheduler(scheduler);
        }
    }

    private static LiquidTransportDefinitions referenceWaterTransport() {
        LiquidTransportDefinitions transport = new LiquidTransportDefinitions();
        transport.put(WaterSystem.TYPE, LiquidTransportProperties.reference());
        return transport;
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
