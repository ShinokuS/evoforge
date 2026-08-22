package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;

final class WorldBoundsHydrologyIntegrationTest {

    @Test
    void edgeWaterCannotEscapeFiniteWorldAndMassRemainsConserved() {
        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(-1, 1, -1, 1, -1, 1);
        MaterialDefinitionId ground =
                assembly.landscapeDefinition("test:bounded_ground");

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                assembly.placeTerrain(x, y, -1, ground);
            }
        }
        assembly.initialWater(1, 0, 0, CellVolume.FULL);

        SimulationRuntime runtime = assembly.start();
        for (int tick = 0; tick < 96; tick++) {
            runtime.stepper().advance();
        }

        long total = 0L;
        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    total += runtime.view().water().amount(x, y, z);
                }
            }
        }

        assertEquals(CellVolume.FULL, total);
        assertEquals(0, runtime.view().water().amount(2, 0, 0));
        assertEquals(0, runtime.view().water().amount(1, -2, 0));
        assertEquals(0, runtime.view().water().amount(1, 0, 2));
    }

    @Test
    void setupMutationsOutsideConfiguredBoundsAreRejected() {
        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(-1, 1, -1, 1, -1, 1);
        MaterialDefinitionId ground =
                assembly.landscapeDefinition("test:bounded_rejection_ground");

        assertThrows(
                IllegalArgumentException.class,
                () -> assembly.placeTerrain(2, 0, 0, ground));
        assertThrows(
                IllegalArgumentException.class,
                () -> assembly.initialWater(0, 0, 2, 1));
    }
}
