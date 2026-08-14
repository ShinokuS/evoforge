package io.github.evoforge.visualizer.scenario.environment;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentation;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationLookup;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Visual acceptance scene for finite surface Water and low-cost rain presentation. */
public final class RainHydrologyScenario implements VisualizerScenario {

    private static final int MIN_X = -12;
    private static final int MAX_X = 12;
    private static final int MIN_Y = -8;
    private static final int MAX_Y = 8;
    private static final int PREWARM_TICKS = 5;

    @Override public String id() { return "rain-hydrology"; }
    @Override public String title() { return "Rain & Water"; }
    @Override public String description() {
        return "Finite rain, infiltration and animated surface water. Space continues the hydrology.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId absorbent =
                assembly.landscapeDefinition("scenario:rain_absorbent");
        LandscapeDefinitionId clay =
                assembly.landscapeDefinition("scenario:rain_clay");
        LandscapeDefinitionId stone =
                assembly.landscapeDefinition("scenario:rain_stone");

        // The absorbent margin is wider than the prewarm propagation distance.
        // Until world generation owns finite bounds, this keeps the acceptance
        // scene away from the temporary sandbox edge without invisible walls.
        assembly.soilHydrology(absorbent, 1_000_000, 60_000);
        assembly.soilHydrology(clay, 500_000, 24_000);
        assembly.periodicPrecipitation(60_000, 1L);

        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int y = MIN_Y; y <= MAX_Y; y++) {
                LandscapeDefinitionId terrain = terrainAt(
                        x,
                        y,
                        absorbent,
                        clay,
                        stone);
                assembly.placeTerrain(x, y, -1, terrain);
            }
        }

        SimulationRuntime runtime = assembly.start();
        for (int tick = 0; tick < PREWARM_TICKS; tick++) {
            runtime.stepper().advance();
        }

        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 0f, 0f, 1f),
                WeatherPresentationLookup.fixed(
                        WeatherPresentation.rain(0.78f)));
    }

    private static LandscapeDefinitionId terrainAt(
            int x,
            int y,
            LandscapeDefinitionId absorbent,
            LandscapeDefinitionId clay,
            LandscapeDefinitionId stone) {

        if (Math.abs(x) > 5 || Math.abs(y) > 3) {
            return absorbent;
        }

        // Two broad catchments create visibly different fill levels while the
        // same deterministic flow solver smooths their shared boundaries.
        if (x <= -2) {
            return stone;
        }
        if (x >= 2) {
            return clay;
        }
        return absorbent;
    }
}
