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

/** Dry-start acceptance scene for rain, local soil capacity, puddles and lake drying. */
public final class RainHydrologyScenario implements VisualizerScenario {

    private static final int MIN_X = -7;
    private static final int MAX_X = 7;
    private static final int MIN_Y = -5;
    private static final int MAX_Y = 5;
    private static final int MIN_Z = -2;
    private static final int MAX_Z = 3;

    // Acceptance-only physical scale: one 1m x 1m tile and one 1m full cell imply
    // 1 mm of water depth == 1000 normalized units.
    private static final long CLIMATE_CYCLE_TICKS = 120L;
    private static final int RAIN_EVENT_VOLUME = 3_000;          // 3.0 mm shower
    private static final long EVAPORATION_INTERVAL_TICKS = 4L;
    private static final int EVAPORATION_PER_EVENT = 250;        // up to 7.5 mm / cycle
    private static final int VISUAL_RAIN_TICKS = 24;

    private static final int SOIL_BASE_CAPACITY = 2_500;
    private static final int SOIL_CAPACITY_VARIATION = 1_500;    // local 1.0..4.0 mm capacity
    private static final int SOIL_INFILTRATION_LIMIT = 3_000;
    private static final long SOIL_VARIATION_SEED = 0x5EEDBEEFL;
    private static final int SOIL_SURFACE_STORAGE = 1_200;       // 1.2 mm micro-storage
    private static final int STONE_SURFACE_STORAGE = 500;

    private static final int LAKE_MIN_X = -6;
    private static final int LAKE_MAX_X = -4;
    private static final int LAKE_MIN_Y = -1;
    private static final int LAKE_MAX_Y = 1;
    private static final int INITIAL_LAKE_DEPTH = 60_000;        // 60 mm

    @Override public String id() { return "rain-hydrology"; }
    @Override public String title() { return "Rain Cycle"; }
    @Override public String description() {
        return "Dry equal SoilMoisture, deterministic local capacity variation, puddle onset, surface retention and a separate finite evaporation lake.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(MIN_X, MAX_X, MIN_Y, MAX_Y, MIN_Z, MAX_Z);
        LandscapeDefinitionId soil =
                assembly.landscapeDefinition("scenario:rain_soil");
        LandscapeDefinitionId stone =
                assembly.landscapeDefinition("scenario:rain_stone");

        assembly.soilHydrology(
                soil,
                SOIL_BASE_CAPACITY,
                SOIL_INFILTRATION_LIMIT);
        assembly.soilHydrologyVariation(
                soil,
                SOIL_VARIATION_SEED,
                SOIL_CAPACITY_VARIATION);
        assembly.surfaceWaterStorage(
                soil,
                SOIL_SURFACE_STORAGE);
        assembly.surfaceWaterStorage(
                stone,
                STONE_SURFACE_STORAGE);
        assembly.periodicPrecipitation(
                RAIN_EVENT_VOLUME,
                CLIMATE_CYCLE_TICKS);
        assembly.periodicEvaporation(
                EVAPORATION_PER_EVENT,
                EVAPORATION_INTERVAL_TICKS);

        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int y = MIN_Y; y <= MAX_Y; y++) {
                if (insideLake(x, y)) {
                    assembly.placeTerrain(x, y, -2, stone);
                    assembly.initialWater(
                            x,
                            y,
                            -1,
                            INITIAL_LAKE_DEPTH);
                } else {
                    assembly.placeTerrain(x, y, -1, soil);
                }
            }
        }

        // A small elevated impermeable roof keeps the old sky-exposure acceptance
        // without pre-wetting the scene. The ground below starts as dry as everywhere else.
        for (int x = 4; x <= 5; x++) {
            for (int y = -1; y <= 1; y++) {
                assembly.placeTerrain(x, y, 1, stone);
            }
        }

        SimulationRuntime runtime = assembly.start();
        WaterScenarioDiagnostics diagnostics = new WaterScenarioDiagnostics(
                runtime, MIN_X, MAX_X, MIN_Y, MAX_Y, -1, MAX_Z);
        WeatherPresentationLookup weather = () -> {
            long phase = Math.floorMod(
                    runtime.time().tick(),
                    CLIMATE_CYCLE_TICKS);
            return phase >= CLIMATE_CYCLE_TICKS - VISUAL_RAIN_TICKS
                    ? WeatherPresentation.rain(0.72f)
                    : WeatherPresentation.CLEAR;
        };

        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 0f, 0f, 1f),
                diagnostics,
                ObjectPresentationBindings.empty(),
                weather);
    }

    private static boolean insideLake(
            int x,
            int y) {
        return x >= LAKE_MIN_X
                && x <= LAKE_MAX_X
                && y >= LAKE_MIN_Y
                && y <= LAKE_MAX_Y;
    }
}
