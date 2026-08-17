package io.github.evoforge.simulation.world.climate;

import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.environment.atmosphere.AtmosphericWaterForcing;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRateCellVolumeCompiler;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Runtime compatibility projection of durable Climate Normals into atmospheric Water forcing.
 *
 * <p>Climate remains immutable generated data. This adapter only interprets its static water normals
 * for the currently requested runtime interval. It owns no weather state and exists so historical
 * climate-direct runs can use the same atmospheric consumer as eventful WeatherState.</p>
 */
public final class ClimateHydroForcingView implements AtmosphericWaterForcing {
    private final ClimateNormalsField climate;
    private final PhysicalSpaceScale spaceScale;
    private final SimulationTimeScale timeScale;
    private long currentTick;

    public ClimateHydroForcingView(ClimateNormalsField climate) {
        this(climate, null, null);
        if (ClimateWaterNormal.Kind.PHYSICAL_WATER_DEPTH_PER_TIME.equals(climate.waterNormalKind())) {
            throw new IllegalArgumentException(
                    "physical climate normals require explicit physical space and simulation time scales");
        }
    }

    public ClimateHydroForcingView(
            ClimateNormalsField climate,
            PhysicalSpaceScale spaceScale,
            SimulationTimeScale timeScale) {
        if (climate == null) {
            throw new IllegalArgumentException("climate normals must not be null");
        }
        if (ClimateWaterNormal.Kind.PHYSICAL_WATER_DEPTH_PER_TIME.equals(climate.waterNormalKind())
                && (spaceScale == null || timeScale == null)) {
            throw new IllegalArgumentException(
                    "physical climate normals require physical space and simulation time scales");
        }
        this.climate = climate;
        this.spaceScale = spaceScale;
        this.timeScale = timeScale;
    }

    @Override
    public WorldBounds bounds() {
        return climate.bounds();
    }

    @Override
    public void advanceToTick(long tick) {
        if (tick <= 0L) {
            throw new IllegalArgumentException("atmospheric forcing tick must be positive");
        }
        currentTick = tick;
    }

    @Override
    public long precipitationDueAt(int x, int y) {
        return precipitationRateAt(x, y).volumeDueAtTick(requireCurrentTick());
    }

    @Override
    public long evaporativeDemandDueAt(int x, int y) {
        return evaporativeDemandRateAt(x, y).volumeDueAtTick(requireCurrentTick());
    }

    public CellVolumeRate precipitationRateAt(int x, int y) {
        return compile(climate.precipitationWaterNormalAt(x, y));
    }

    public CellVolumeRate evaporativeDemandRateAt(int x, int y) {
        return compile(climate.evaporativeDemandWaterNormalAt(x, y));
    }

    private long requireCurrentTick() {
        if (currentTick <= 0L) {
            throw new IllegalStateException("atmospheric forcing must advance before interval amounts are read");
        }
        return currentTick;
    }

    private CellVolumeRate compile(ClimateWaterNormal normal) {
        if (normal instanceof ClimateWaterNormal.LegacyCellVolume legacy) {
            return legacy.rate();
        }
        if (normal instanceof ClimateWaterNormal.PhysicalDepth physical) {
            return WaterDepthRateCellVolumeCompiler.compile(physical.rate(), spaceScale, timeScale);
        }
        throw new IllegalStateException("unsupported climate water normal: " + normal);
    }
}
