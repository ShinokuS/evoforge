package io.github.evoforge.visualizer.scenario.environment;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.mechanics.hydrology.PrecipitationSchedule;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentation;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationLookup;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;
import io.github.evoforge.visualizer.scenario.water.WaterScenarioDiagnostics;

/** Dry-start acceptance scene for rain, Soil capacity, puddles and lake drying. */
public final class RainHydrologyScenario implements VisualizerScenario {
    private static final int MIN_X=-7, MAX_X=7, MIN_Y=-5, MAX_Y=5, MIN_Z=-2, MAX_Z=3;
    private static final long CYCLE=360L, RAIN_TICKS=120L, RAIN_INTERVAL=1L, EVAP_INTERVAL=4L;
    private static final int RAIN_VOLUME=20, EVAP_VOLUME=60;
    private static final int LOW_CAPACITY=1_000, HIGH_CAPACITY=4_000, PERMEABILITY=3_000;
    private static final int SOIL_RETENTION=1_200, STONE_RETENTION=500;
    private static final int LAKE_MIN_X=-6, LAKE_MAX_X=-4, LAKE_MIN_Y=-1, LAKE_MAX_Y=1;
    private static final int LAKE_DEPTH=60_000;

    @Override public String id() { return "rain-hydrology"; }
    @Override public String title() { return "Rain Cycle"; }
    @Override public String description() {
        return "Long light rain over explicitly prepared low- and high-capacity Soil; puddles emerge where capacity is exhausted, while a finite lake demonstrates evaporation.";
    }

    @Override
    public ScenarioSession create() {
        PrecipitationSchedule rain = PrecipitationSchedule.cyclic(
                RAIN_VOLUME, RAIN_INTERVAL, RAIN_TICKS, CYCLE);
        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(MIN_X, MAX_X, MIN_Y, MAX_Y, MIN_Z, MAX_Z);
        LandscapeDefinitionId low = assembly.landscapeDefinition("scenario:rain_soil_low_capacity");
        LandscapeDefinitionId high = assembly.landscapeDefinition("scenario:rain_soil_high_capacity");
        LandscapeDefinitionId stone = assembly.landscapeDefinition("scenario:rain_stone");

        assembly.soilProperties(low, LOW_CAPACITY, PERMEABILITY);
        assembly.soilProperties(high, HIGH_CAPACITY, PERMEABILITY);
        assembly.surfaceRetention(low, SOIL_RETENTION);
        assembly.surfaceRetention(high, SOIL_RETENTION);
        assembly.surfaceRetention(stone, STONE_RETENTION);
        assembly.precipitation(rain);
        assembly.periodicEvaporation(EVAP_VOLUME, EVAP_INTERVAL);

        for (int x=MIN_X; x<=MAX_X; x++) {
            for (int y=MIN_Y; y<=MAX_Y; y++) {
                if (insideLake(x,y)) {
                    assembly.placeTerrain(x,y,-2,stone);
                    assembly.initialWater(x,y,-1,LAKE_DEPTH);
                } else {
                    assembly.placeTerrain(x,y,-1,x<0 ? low : high);
                }
            }
        }

        SimulationRuntime runtime = assembly.start();
        WaterScenarioDiagnostics diagnostics = new WaterScenarioDiagnostics(
                runtime, MIN_X, MAX_X, MIN_Y, MAX_Y, -1, MAX_Z);
        WeatherPresentationLookup weather = () -> rain.activeAt(runtime.time().tick())
                ? WeatherPresentation.rain(0.60f) : WeatherPresentation.CLEAR;
        return new ScenarioSession(runtime, new ScenarioView(0,0f,0f,1f), diagnostics,
                ObjectPresentationBindings.empty(), weather);
    }

    private static boolean insideLake(int x,int y) {
        return x>=LAKE_MIN_X && x<=LAKE_MAX_X && y>=LAKE_MIN_Y && y<=LAKE_MAX_Y;
    }
}
