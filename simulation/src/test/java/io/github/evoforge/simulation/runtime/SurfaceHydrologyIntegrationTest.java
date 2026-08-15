package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;

final class SurfaceHydrologyIntegrationTest {

    @Test
    void configuredPrecipitationRunsOnCadenceAndFeedsRetainedWater() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId soil =
                assembly.landscapeDefinition("test:soil");
        assembly.soilProperties(
                soil,
                500_000,
                100_000);
        assembly.periodicPrecipitation(
                80_000,
                2L);
        assembly.placeTerrain(3, 4, 0, soil);

        SimulationRuntime runtime = assembly.start();

        assertEquals(1, runtime.view().terrainSurfaces().columnCount());
        assertEquals(0, retainedWater(runtime, 3, 4, 0));

        runtime.stepper().advance();
        assertEquals(1L, runtime.time().tick());
        assertEquals(0, retainedWater(runtime, 3, 4, 0));

        runtime.stepper().advance();
        assertEquals(2L, runtime.time().tick());
        assertEquals(80_000, retainedWater(runtime, 3, 4, 0));
        assertEquals(0, runtime.view().water().amount(3, 4, 1));

        runtime.stepper().advance();
        runtime.stepper().advance();
        assertEquals(4L, runtime.time().tick());
        assertEquals(160_000, retainedWater(runtime, 3, 4, 0));
    }

    @Test
    void periodicEvaporationDriesRetainedWaterAndSkipsSharedRainTicks() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId soil =
                assembly.landscapeDefinition("test:soil");
        assembly.soilProperties(
                soil,
                1_000_000,
                1_000_000);
        assembly.periodicPrecipitation(
                100_000,
                2L);
        assembly.periodicEvaporation(
                30_000,
                3L);
        assembly.placeTerrain(0, 0, 0, soil);

        SimulationRuntime runtime = assembly.start();

        runtime.stepper().advance();
        runtime.stepper().advance();
        assertEquals(2L, runtime.time().tick());
        assertEquals(100_000, retainedWater(runtime, 0, 0, 0));

        runtime.stepper().advance();
        assertEquals(3L, runtime.time().tick());
        assertEquals(70_000, retainedWater(runtime, 0, 0, 0));

        runtime.stepper().advance();
        assertEquals(4L, runtime.time().tick());
        assertEquals(170_000, retainedWater(runtime, 0, 0, 0));

        runtime.stepper().advance();
        runtime.stepper().advance();
        assertEquals(6L, runtime.time().tick());
        assertEquals(
                270_000,
                retainedWater(runtime, 0, 0, 0));
    }

    @Test
    void runtimeWithoutEnvironmentSchedulesRemainsHydrologicallyIdle() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId terrain =
                assembly.landscapeDefinition("test:terrain");
        assembly.placeTerrain(0, 0, 0, terrain);

        SimulationRuntime runtime = assembly.start();
        for (int tick = 0; tick < 20; tick++) {
            runtime.stepper().advance();
        }

        assertEquals(0, retainedWater(runtime, 0, 0, 0));
        assertEquals(0, runtime.view().water().amount(0, 0, 1));
        assertEquals(0, runtime.view().waterSurfaces().columnCount());
    }

    private static int retainedWater(
            SimulationRuntime runtime,
            int x,
            int y,
            int z) {
        return runtime.view().soilLiquids().amountOf(
                WaterSystem.TYPE,
                x,
                y,
                z);
    }
}
