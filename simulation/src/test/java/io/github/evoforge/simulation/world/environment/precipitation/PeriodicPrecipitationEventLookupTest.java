package io.github.evoforge.simulation.world.environment.precipitation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.time.SimulationTime;
import io.github.evoforge.simulation.world.environment.sky.VerticalSkySurfaceSystem;
import io.github.evoforge.simulation.world.landscape.LandscapeSystem;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.soil.SoilHydrologyDefinitions;
import io.github.evoforge.simulation.world.landscape.soil.SoilMoistureSystem;
import io.github.evoforge.simulation.world.landscape.soil.storage.SparseSoilMoistureStorage;
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.landscape.water.storage.SparseWaterStorage;

final class PeriodicPrecipitationEventLookupTest {

    @Test
    void reportsBothUpcomingAndJustCompletedEventTick() {
        DefinitionRegistry<LandscapeDefinitionId> definitions =
                new DefinitionRegistry<>(
                        LandscapeDefinitionId::of,
                        LandscapeDefinitionId::asInt);
        LandscapeSystem landscape = LandscapeSystem.create(
                new SparseTerrainStorage(),
                definitions);
        SoilHydrologyDefinitions soilHydrology =
                new SoilHydrologyDefinitions();
        SoilMoistureSystem moisture = new SoilMoistureSystem(
                new SparseSoilMoistureStorage(),
                landscape.terrain(),
                soilHydrology);
        WaterSystem water = new WaterSystem(
                new SparseWaterStorage(),
                landscape.geometry());
        PrecipitationSystem precipitation = new PrecipitationSystem(
                landscape.terrain(),
                landscape.geometry(),
                moisture,
                water);
        SkyPrecipitationSystem sky = new SkyPrecipitationSystem(
                new VerticalSkySurfaceSystem(
                        landscape.terrainSurfaces(),
                        water.surfaces()),
                precipitation);
        MutableTime time = new MutableTime();
        PeriodicPrecipitationSystem periodic =
                new PeriodicPrecipitationSystem(
                        sky,
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

    private static final class MutableTime implements SimulationTime {
        private long tick;

        @Override
        public long tick() {
            return tick;
        }
    }
}
