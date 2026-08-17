package io.github.evoforge.simulation.world.calibration.soil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.soil.SoilProperties;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialBindings;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SoilHydraulicRuntimeBinderTest {
    private static final TerrainMaterialKey SOIL = TerrainMaterialKey.of("test:soil");
    private static final PhysicalSpaceScale SPACE = PhysicalSpaceScale.cubicMillimeters(1_000L);
    private static final SimulationTimeScale TIME = SimulationTimeScale.of(Duration.ofHours(1));

    @Test
    void bindsCalibratedPhysicalFactsBeforeRuntimeStarts() {
        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(0, 0, 0, 0, 0, 0);
        LandscapeDefinitionId terrain = assembly.landscapeDefinition("test:soil-runtime");
        TerrainMaterialBindings materials = TerrainMaterialBindings.of(Map.of(SOIL, terrain));
        SoilHydraulicProfile profile = new SoilHydraulicProfile(
                450_000,
                300_000,
                120_000,
                WaterDepthRate.ofMillimeters(8L, Duration.ofHours(1)));

        SoilHydraulicRuntimeBinder.bind(
                assembly,
                materials,
                SoilHydraulicProfileBindings.of(Map.of(SOIL, profile)),
                SPACE,
                TIME);
        assembly.placeTerrain(0, 0, 0, terrain);
        SimulationRuntime runtime = assembly.start();

        assertEquals(
                new SoilProperties(450_000, 8_000),
                runtime.view().soilProperties().find(0, 0, 0));
    }

    @Test
    void rejectsCalibratedSoilWithoutRuntimeMaterialBinding() {
        SoilHydraulicProfile profile = new SoilHydraulicProfile(
                450_000,
                300_000,
                120_000,
                WaterDepthRate.ofMillimeters(8L, Duration.ofHours(1)));

        assertThrows(IllegalArgumentException.class, () -> SoilHydraulicRuntimeBinder.bind(
                SimulationAssembly.create(),
                TerrainMaterialBindings.of(Map.of()),
                SoilHydraulicProfileBindings.of(Map.of(SOIL, profile)),
                SPACE,
                TIME));
    }
}
