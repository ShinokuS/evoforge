package io.github.evoforge.simulation.world.weather.model;

import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.bootstrap.AtmosphericRuntimeComposition;
import io.github.evoforge.simulation.world.bootstrap.AtmosphericRuntimePlan;
import io.github.evoforge.simulation.world.calibration.rainfall.RainfallRegime;
import io.github.evoforge.simulation.world.calibration.rainfall.RainfallRegimeField;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.weather.WeatherFootprint;
import io.github.evoforge.simulation.world.weather.WeatherState;
import io.github.evoforge.simulation.world.weather.WeatherWaterForcing;
import java.util.Optional;

/**
 * Runtime composition for the current coherent whole-world alternating rainfall model.
 *
 * <p>This adapter intentionally requires one uniform calibrated rainfall regime. Spatially varying
 * regimes belong to a spatial weather model rather than being silently flattened here.</p>
 */
public final class AlternatingRainfallPulseAtmosphericPlan implements AtmosphericRuntimePlan {
    private final RainfallRegimeField regimes;
    private final long seed;

    public AlternatingRainfallPulseAtmosphericPlan(RainfallRegimeField regimes, long seed) {
        if (regimes == null) throw new IllegalArgumentException("rainfall regimes must not be null");
        this.regimes = regimes;
        this.seed = seed;
    }

    @Override
    public AtmosphericRuntimeComposition compose(
            WorldAtlas atlas,
            Optional<SimulationTimeScale> timeScale) {
        if (atlas == null || timeScale == null) {
            throw new IllegalArgumentException("atmospheric plan inputs must not be null");
        }
        WorldBounds bounds = atlas.genesis().spec().bounds();
        if (!bounds.equals(regimes.bounds())) {
            throw new IllegalArgumentException("rainfall regime bounds must match runtime world");
        }

        SimulationTimeScale physicalTime = timeScale.orElseThrow(() ->
                new IllegalStateException("alternating rainfall requires an explicit simulation time scale"));
        PhysicalSpaceScale spaceScale = atlas.genesis().spec().requirePhysicalSpaceScale();
        RainfallRegime regime = requireUniformRegime();

        WeatherState weather = WeatherState.calmFromClimateNormals(atlas.climateNormals());
        AlternatingRainfallPulseDriver driver = new AlternatingRainfallPulseDriver(
                weather,
                WeatherFootprint.whole(bounds),
                AlternatingRainfallPulseCompiler.compile(regime),
                physicalTime,
                seed);
        return AtmosphericRuntimeComposition.weather(
                new WeatherWaterForcing(weather, spaceScale, physicalTime, driver),
                weather);
    }

    private RainfallRegime requireUniformRegime() {
        WorldBounds bounds = regimes.bounds();
        RainfallRegime expected = regimes.at(bounds.minX(), bounds.minY());
        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            int worldY = (int) y;
            for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                RainfallRegime actual = regimes.at((int) x, worldY);
                if (!expected.equals(actual)) {
                    throw new IllegalArgumentException(
                            "alternating whole-world rainfall requires a uniform calibrated regime");
                }
            }
        }
        return expected;
    }
}
