package io.github.evoforge.visualizer.scenario.environment;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.environment.precipitation.PrecipitationSchedule;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentation;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationLookup;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;
import io.github.evoforge.visualizer.scenario.water.WaterScenarioDiagnostics;

/** Dry-start acceptance scene for rain, local Soil capacity, puddles and lake drying. */
public final class RainHydrologyScenario implements VisualizerScenario {

    private static final int MIN_X = -7;
    private static final int MAX_X = 7;
    private static final int MIN_Y = -5;
    private static final int MAX_Y = 5;
    private static final int MIN_Z = -2;
    private static final int MAX_Z = 3;

    // Acceptance-only physical scale: one 1m x 1m tile and one 1m full cell imply
    // 1 mm of liquid depth == 1000 normalized units. The light 2.4mm shower is spread
    // across 120 simulation ticks instead of arriving as one artificial pulse.
    private static final long CLIMATE_CYCLE_TICKS = 360L;
    private static final long RAIN_ACTIVE_TICKS = 120L;
    private static final long RAIN_PULSE_INTERVAL_TICKS = 1L;
    private static final int RAIN_PULSE_VOLUME = 20;             // 0.020 mm/tick, 2.4 mm total
    private static final long EVAPORATION_INTERVAL_TICKS = 4L;
    private static final int EVAPORATION_PER_EVENT = 60;         // 3.6 mm potential over dry window

    private static final int SOIL_BASE_CAPACITY = 2_500;
    private static final int SOIL_CAPACITY_VARIATION = 1_500;    // local 1.0..4.0 mm capacity
    private static final int SOIL_PERMEABILITY = 3_000;
    private static final long SOIL_VARIATION_SEED = 0x5EEDBEEFL;
    private static final int SOIL_SURFACE_RETENTION = 1_200;     // 1.2 mm microtopography
    private static final int STONE_SURFACE_RETENTION = 500;

    private static final int LAKE_MIN_X = -6;
    private static final int LAKE_MAX_X = -4;
    private static final int LAKE_MIN_Y = -1;
    private static final int LAKE_MAX_Y = 1;
    private static final int INITIAL_LAKE_DEPTH = 60_000;        // 60 mm

    @Override public String id() { return "rain-hydrology"; }
    @Override public String title() { return "Rain Cycle"; }
    @Override public String description() {
        return "Long light rain: uniformly dry terrain has deterministic local Soil capacity, puddles emerge unevenly during the shower, and a separate finite lake demonstrates evaporation.";
    }

    @Override
    public ScenarioSession create() {
        PrecipitationSchedule rainSchedule = PrecipitationSchedule.cyclic(
                RAIN_PULSE_VOLUME,
                RAIN_PULSE_INTERVAL_TICKS,
                RAIN_ACTIVE_TICKS,
                CLIMATE_CYCLE_TICKS);

        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(MIN_X, MAX_X, MIN_Y, MAX_Y, MIN_Z, MAX_Z);
        LandscapeDefinitionId soil =
                assembly.landscapeDefinition("scenario:rain_soil");
        LandscapeDefinitionId stone =
                assembly.landscapeDefinition("scenario:rain_stone");

        assembly.soilProperties(
                soil,
                SOIL_BASE_CAPACITY,
                SOIL_PERMEABILITY);
        assembly.soilPropertiesVariation(
                soil,
                SOIL_VARIATION_SEED,
                SOIL_CAPACITY_VARIATION);
        assembly.surfaceRetention(
                soil,
                SOIL_SURFACE_RETENTION);
        assembly.surfaceRetention(
                stone,
                STONE_SURFACE_RETENTION);
        assembly.precipitation(rainSchedule);
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

        SimulationRuntime runtime = assembly.start();
        WaterScenarioDiagnostics diagnostics = new WaterScenarioDiagnostics(
                runtime, MIN_X, MAX_X, MIN_Y, MAX_Y, -1, MAX_Z);
        WeatherPresentationLookup weather = () ->
                rainSchedule.activeAt(runtime.time().tick())
                        ? WeatherPresentation.rain(0.60f)
                        : WeatherPresentation.CLEAR;

        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 0f, 0f, 1f),
                diagnostics,
                ObjectPresentationBindings.empty(),
                weather);
    }

    private static boolean insideLake(int x, int y) {
        return x >= LAKE_MIN_X
                && x <= LAKE_MAX_X
                && y >= LAKE_MIN_Y
                && y <= LAKE_MAX_Y;
    }
}
