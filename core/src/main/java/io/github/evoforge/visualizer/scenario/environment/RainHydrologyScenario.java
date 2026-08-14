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

    private static final int MIN_X = -5;
    private static final int MAX_X = 5;
    private static final int MIN_Y = -3;
    private static final int MAX_Y = 3;
    private static final int WALL_X = 6;
    private static final int WALL_Y = 4;
    private static final int PREWARM_TICKS = 400;

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

        // Rain is intentionally weak. FullShape wall cells absorb it for many
        // minutes of normal visualizer runtime, while their closed physical
        // faces contain the interior surface Water without inventing world bounds.
        assembly.soilHydrology(absorbent, 1_000_000, 500);

        // Clay fully absorbs the first 150k volume (300 rain evaluations) and
        // only then begins producing free Water. After the deterministic prewarm
        // this leaves a visibly shallower pool than the stone catchment, making
        // fill-dependent opacity easy to compare without presentation-only data.
        assembly.soilHydrology(clay, 150_000, 500);
        assembly.periodicPrecipitation(500, 1L);

        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int y = MIN_Y; y <= MAX_Y; y++) {
                assembly.placeTerrain(
                        x,
                        y,
                        -1,
                        floorAt(x, absorbent, clay, stone));
            }
        }
        placeAbsorbentWalls(assembly, absorbent);

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

    private static LandscapeDefinitionId floorAt(
            int x,
            LandscapeDefinitionId absorbent,
            LandscapeDefinitionId clay,
            LandscapeDefinitionId stone) {

        // The two catchments receive the same rain but retain different surface
        // fractions. Their shared absorbent strip makes the alpha difference and
        // continuing hydraulic equalization easy to inspect visually.
        if (x <= -2) {
            return stone;
        }
        if (x >= 2) {
            return clay;
        }
        return absorbent;
    }

    private static void placeAbsorbentWalls(
            SimulationAssembly assembly,
            LandscapeDefinitionId absorbent) {

        for (int x = -WALL_X; x <= WALL_X; x++) {
            assembly.placeTerrain(x, -WALL_Y, 0, absorbent);
            assembly.placeTerrain(x, WALL_Y, 0, absorbent);
        }
        for (int y = MIN_Y; y <= MAX_Y; y++) {
            assembly.placeTerrain(-WALL_X, y, 0, absorbent);
            assembly.placeTerrain(WALL_X, y, 0, absorbent);
        }
    }
}
