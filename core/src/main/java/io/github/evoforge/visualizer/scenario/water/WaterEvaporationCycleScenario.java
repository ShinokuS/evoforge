package io.github.evoforge.visualizer.scenario.water;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentation;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationLookup;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Periodic rain pulse followed by exposed Water/Soil evaporation. */
public final class WaterEvaporationCycleScenario implements VisualizerScenario {

    private static final long RAIN_INTERVAL = 20L;

    @Override public String id() { return "water-evaporation-cycle"; }
    @Override public String title() { return "Water Evaporation Cycle"; }
    @Override public String description() {
        return "Rain pulses every 20 ticks; between pulses exposed Water evaporates before retained soil moisture.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId stone =
                assembly.landscapeDefinition("scenario:water_evap_stone");
        LandscapeDefinitionId soil =
                assembly.landscapeDefinition("scenario:water_evap_soil");
        assembly.soilHydrology(soil, 1_000_000, 1_000_000);
        assembly.periodicPrecipitation(150_000, RAIN_INTERVAL);
        assembly.periodicEvaporation(15_000, 1L);

        for (int x = -5; x <= 5; x++) {
            LandscapeDefinitionId floor = x < 0 ? stone : soil;
            WaterScenarioSupport.fillFloor(
                    assembly, floor, x, x, -3, 3, -1);
        }
        WaterScenarioSupport.ringWalls(
                assembly, soil, -6, 6, -4, 4, 0, 0);
        for (int y = -3; y <= 3; y++) {
            assembly.placeTerrain(0, y, 0, soil);
        }

        SimulationRuntime runtime =
                WaterScenarioSupport.startAndAdvance(assembly, (int) RAIN_INTERVAL);
        WaterScenarioDiagnostics diagnostics = new WaterScenarioDiagnostics(
                runtime, -6, 6, -4, 4, -1, 1);
        WeatherPresentationLookup weather = () ->
                runtime.time().tick() % RAIN_INTERVAL == 0L
                        ? WeatherPresentation.rain(0.68f)
                        : WeatherPresentation.CLEAR;

        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 0f, 0f, 1f),
                diagnostics,
                ObjectPresentationBindings.empty(),
                weather);
    }
}
