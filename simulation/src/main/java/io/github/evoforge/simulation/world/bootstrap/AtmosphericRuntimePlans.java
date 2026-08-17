package io.github.evoforge.simulation.world.bootstrap;

import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.climate.ClimateHydroForcingView;
import io.github.evoforge.simulation.world.climate.ClimateWaterNormal;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.weather.WeatherHydroForcingView;
import io.github.evoforge.simulation.world.weather.WeatherState;
import java.util.Optional;

/** Built-in runtime atmosphere plans; applications may supply any AtmosphericRuntimePlan directly. */
public final class AtmosphericRuntimePlans {
    private AtmosphericRuntimePlans() { }

    public static AtmosphericRuntimePlan disabled() {
        return (atlas, timeScale) -> AtmosphericRuntimeComposition.disabled();
    }

    /** Historical direct ClimateNormals-to-Water projection retained for compatibility runs. */
    public static AtmosphericRuntimePlan climateNormalsCompatibility() {
        return (atlas, timeScale) -> AtmosphericRuntimeComposition.forcing(
                climateForcing(atlas, timeScale));
    }

    /** Creates current WeatherState initialized calm from local climate means. */
    public static AtmosphericRuntimePlan calmWeatherState() {
        return (atlas, timeScale) -> {
            WeatherState weather = WeatherState.calmFromClimateNormals(atlas.climateNormals());
            PhysicalSpaceScale spaceScale = atlas.genesis().spec().requirePhysicalSpaceScale();
            SimulationTimeScale physicalTime = requirePhysicalTime(
                    timeScale,
                    "weather-state forcing requires an explicit simulation time scale");
            return AtmosphericRuntimeComposition.weather(
                    new WeatherHydroForcingView(weather, spaceScale, physicalTime),
                    weather);
        };
    }

    private static ClimateHydroForcingView climateForcing(
            WorldAtlas atlas,
            Optional<SimulationTimeScale> timeScale) {
        if (!ClimateWaterNormal.Kind.PHYSICAL_WATER_DEPTH_PER_TIME.equals(
                atlas.climateNormals().waterNormalKind())) {
            return new ClimateHydroForcingView(atlas.climateNormals());
        }
        PhysicalSpaceScale spaceScale = atlas.genesis().spec().requirePhysicalSpaceScale();
        SimulationTimeScale physicalTime = requirePhysicalTime(
                timeScale,
                "physical climate forcing requires an explicit simulation time scale");
        return new ClimateHydroForcingView(atlas.climateNormals(), spaceScale, physicalTime);
    }

    private static SimulationTimeScale requirePhysicalTime(
            Optional<SimulationTimeScale> timeScale,
            String message) {
        return timeScale.orElseThrow(() -> new IllegalStateException(message));
    }
}
