package io.github.evoforge.visualizer.scenario.environment;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentation;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationLookup;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;
import io.github.evoforge.visualizer.scenario.water.WaterScenarioDiagnostics;

/** Bounded climate acceptance scene with intermittent light rain and net drying. */
public final class RainHydrologyScenario implements VisualizerScenario {

    private static final int MIN_X = -6;
    private static final int MAX_X = 6;
    private static final int MIN_Y = -4;
    private static final int MAX_Y = 4;
    private static final int MIN_Z = -1;
    private static final int MAX_Z = 3;

    // Acceptance-only physical scale: one 1m x 1m tile and one 1m full cell imply
    // 1 mm of water depth == 1/1000 CellVolume.FULL == 1000 normalized units.
    // A 240-tick climate cycle is treated as one scenario day for rate balancing.
    private static final long CLIMATE_CYCLE_TICKS = 240L;
    private static final int RAIN_EVENT_VOLUME = 3_000;       // 3.0 mm / cycle
    private static final int EVAPORATION_PER_TICK = 20;       // 4.8 mm / cycle gross
    private static final int VISUAL_RAIN_TICKS = 20;
    private static final int PREWARM_TICKS = 240;

    @Override public String id() { return "rain-hydrology"; }
    @Override public String title() { return "Rain Cycle"; }
    @Override public String description() {
        return "Intermittent 3 mm rain pulse, soil infiltration, roof shielding and evaporation; puddles dry before the next cycle.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(MIN_X, MAX_X, MIN_Y, MAX_Y, MIN_Z, MAX_Z);
        LandscapeDefinitionId loam =
                assembly.landscapeDefinition("scenario:rain_loam");
        LandscapeDefinitionId clay =
                assembly.landscapeDefinition("scenario:rain_compacted_clay");
        LandscapeDefinitionId stone =
                assembly.landscapeDefinition("scenario:rain_stone");

        // Retained volumes are expressed as equivalent water depth in this
        // acceptance scale: 120 mm loam storage and 80 mm compacted-clay storage.
        // Loam accepts the entire 3 mm shower; compacted clay accepts only 0.8 mm,
        // so it produces a small transient puddle instead of long-term flooding.
        assembly.soilHydrology(loam, 120_000, 3_000);
        assembly.soilHydrology(clay, 80_000, 800);
        assembly.periodicPrecipitation(
                RAIN_EVENT_VOLUME,
                CLIMATE_CYCLE_TICKS);
        assembly.periodicEvaporation(
                EVAPORATION_PER_TICK,
                1L);

        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int y = MIN_Y; y <= MAX_Y; y++) {
                assembly.placeTerrain(
                        x,
                        y,
                        -1,
                        floorAt(x, loam, clay, stone));
            }
        }

        // A small roof verifies that precipitation targets the highest exposed
        // terrain instead of the protected ground beneath it.
        for (int x = 3; x <= 4; x++) {
            for (int y = -1; y <= 1; y++) {
                assembly.placeTerrain(x, y, 1, loam);
            }
        }

        SimulationRuntime runtime = assembly.start();
        for (int tick = 0; tick < PREWARM_TICKS; tick++) {
            runtime.stepper().advance();
        }

        WaterScenarioDiagnostics diagnostics = new WaterScenarioDiagnostics(
                runtime, MIN_X, MAX_X, MIN_Y, MAX_Y, 0, MAX_Z);
        WeatherPresentationLookup weather = () -> {
            long phase = Math.floorMod(
                    runtime.time().tick(),
                    CLIMATE_CYCLE_TICKS);
            if (phase < VISUAL_RAIN_TICKS) {
                return WeatherPresentation.rain(0.38f);
            }
            return WeatherPresentation.CLEAR;
        };

        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 0f, 0f, 1f),
                diagnostics,
                ObjectPresentationBindings.empty(),
                weather);
    }

    private static LandscapeDefinitionId floorAt(
            int x,
            LandscapeDefinitionId loam,
            LandscapeDefinitionId clay,
            LandscapeDefinitionId stone) {

        if (x <= -2) {
            return stone;
        }
        if (x <= 1) {
            return clay;
        }
        return loam;
    }
}
