package io.github.evoforge.simulation.world.climate;

import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.HydroClimateField;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRateCellVolumeCompiler;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Narrow runtime-facing hydrologic projection of authoritative climate normals.
 *
 * <p>Legacy V1-V7 climate can pass through its historical CellVolume/tick rates unchanged. V8
 * physical depth normals are converted only here, using explicit world-space and runtime-time
 * scales. The view owns no climate state and remains replaceable by future WeatherState forcing.</p>
 */
public final class ClimateHydroForcingView implements HydroClimateField {
    private final ClimateNormalsField climate;
    private final PhysicalSpaceScale spaceScale;
    private final SimulationTimeScale timeScale;

    /** Legacy V1-V7 projection retaining historical tick-relative semantics. */
    public ClimateHydroForcingView(ClimateNormalsField climate) {
        this(climate, null, null);
        if (ClimateWaterNormal.Kind.PHYSICAL_WATER_DEPTH_PER_TIME.equals(climate.waterNormalKind())) {
            throw new IllegalArgumentException(
                    "physical climate normals require explicit physical space and simulation time scales");
        }
    }

    /** Physical V8+ projection from depth/time normals into CellVolume/tick runtime forcing. */
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
    public CellVolumeRate precipitationSupplyAt(int x, int y) {
        return compile(climate.precipitationWaterNormalAt(x, y));
    }

    @Override
    public CellVolumeRate evaporativeDemandAt(int x, int y) {
        return compile(climate.evaporativeDemandWaterNormalAt(x, y));
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
