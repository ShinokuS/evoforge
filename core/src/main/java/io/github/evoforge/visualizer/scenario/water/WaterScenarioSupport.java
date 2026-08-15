package io.github.evoforge.visualizer.scenario.water;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentation;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationLookup;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;

/** Focused construction helpers shared only by Water visual acceptance scenarios. */
final class WaterScenarioSupport {

    private WaterScenarioSupport() {
    }

    static void fillFloor(
            SimulationAssembly assembly,
            LandscapeDefinitionId definition,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int z) {

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                assembly.placeTerrain(x, y, z, definition);
            }
        }
    }

    static void ringWalls(
            SimulationAssembly assembly,
            LandscapeDefinitionId definition,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ) {

        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                assembly.placeTerrain(x, minY, z, definition);
                assembly.placeTerrain(x, maxY, z, definition);
            }
            for (int y = minY + 1; y < maxY; y++) {
                assembly.placeTerrain(minX, y, z, definition);
                assembly.placeTerrain(maxX, y, z, definition);
            }
        }
    }

    static SimulationRuntime startAndAdvance(
            SimulationAssembly assembly,
            int ticks) {

        SimulationRuntime runtime = assembly.start();
        for (int tick = 0; tick < ticks; tick++) {
            runtime.stepper().advance();
        }
        return runtime;
    }

    static ScenarioSession rainySession(
            SimulationRuntime runtime,
            ScenarioView view,
            WaterScenarioDiagnostics diagnostics,
            float rainIntensity) {

        return new ScenarioSession(
                runtime,
                view,
                diagnostics,
                ObjectPresentationBindings.empty(),
                WeatherPresentationLookup.fixed(
                        WeatherPresentation.rain(rainIntensity)));
    }

    static ScenarioSession clearSession(
            SimulationRuntime runtime,
            ScenarioView view,
            WaterScenarioDiagnostics diagnostics) {

        return new ScenarioSession(
                runtime,
                view,
                diagnostics,
                ObjectPresentationBindings.empty(),
                WeatherPresentationLookup.CLEAR_LOOKUP);
    }
}
