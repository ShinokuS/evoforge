package io.github.evoforge.simulation.world.weather;

import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.HydroClimateField;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRateCellVolumeCompiler;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Runtime hydrologic projection of current WeatherState.
 *
 * <p>Weather owns physical current rates. This view alone converts them into the historical
 * CellVolume/tick protocol consumed by precipitation and evaporation systems.</p>
 */
public final class WeatherHydroForcingView implements HydroClimateField {
    private final WeatherState weather;
    private final PhysicalSpaceScale spaceScale;
    private final SimulationTimeScale timeScale;

    public WeatherHydroForcingView(
            WeatherState weather,
            PhysicalSpaceScale spaceScale,
            SimulationTimeScale timeScale) {
        if (weather == null || spaceScale == null || timeScale == null) {
            throw new IllegalArgumentException("weather forcing dependencies must not be null");
        }
        this.weather = weather;
        this.spaceScale = spaceScale;
        this.timeScale = timeScale;
    }

    @Override
    public WorldBounds bounds() {
        return weather.bounds();
    }

    @Override
    public CellVolumeRate precipitationSupplyAt(int x, int y) {
        return WaterDepthRateCellVolumeCompiler.compile(
                weather.at(x, y).precipitationRate(),
                spaceScale,
                timeScale);
    }

    @Override
    public CellVolumeRate evaporativeDemandAt(int x, int y) {
        return WaterDepthRateCellVolumeCompiler.compile(
                weather.at(x, y).evaporativeDemandRate(),
                spaceScale,
                timeScale);
    }
}
