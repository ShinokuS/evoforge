package io.github.evoforge.simulation.world.climate;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Durable long-term climate facts by global XY column.
 *
 * <p>This is generated world state, not tick weather and not an instruction to run atmospheric
 * forcing. V1-V7 may expose historical cell-relative water normals; V8+ may expose physical
 * water-depth normals. One field never mixes those dimensions.</p>
 */
public interface ClimateNormalsField {
    WorldBounds bounds();

    ClimateTemperature meanTemperatureAt(int x, int y);

    ClimateWaterNormal.Kind waterNormalKind();

    ClimateWaterNormal precipitationWaterNormalAt(int x, int y);

    ClimateWaterNormal evaporativeDemandWaterNormalAt(int x, int y);

    /** Legacy V1-V7 accessor. */
    default CellVolumeRate precipitationNormalAt(int x, int y) {
        ClimateWaterNormal normal = precipitationWaterNormalAt(x, y);
        if (normal instanceof ClimateWaterNormal.LegacyCellVolume legacy) return legacy.rate();
        throw new IllegalStateException("precipitation normal uses physical water depth");
    }

    /** Legacy V1-V7 accessor. */
    default CellVolumeRate evaporativeDemandNormalAt(int x, int y) {
        ClimateWaterNormal normal = evaporativeDemandWaterNormalAt(x, y);
        if (normal instanceof ClimateWaterNormal.LegacyCellVolume legacy) return legacy.rate();
        throw new IllegalStateException("evaporative-demand normal uses physical water depth");
    }

    default WaterDepthRate precipitationDepthNormalAt(int x, int y) {
        ClimateWaterNormal normal = precipitationWaterNormalAt(x, y);
        if (normal instanceof ClimateWaterNormal.PhysicalDepth physical) return physical.rate();
        throw new IllegalStateException("precipitation normal uses legacy CellVolume/tick");
    }

    default WaterDepthRate evaporativeDemandDepthNormalAt(int x, int y) {
        ClimateWaterNormal normal = evaporativeDemandWaterNormalAt(x, y);
        if (normal instanceof ClimateWaterNormal.PhysicalDepth physical) return physical.rate();
        throw new IllegalStateException("evaporative-demand normal uses legacy CellVolume/tick");
    }

    /** @deprecated Supply is a runtime-forcing concept. */
    @Deprecated(forRemoval = true)
    default CellVolumeRate precipitationSupplyAt(int x, int y) {
        return precipitationNormalAt(x, y);
    }

    /** @deprecated Runtime demand is a forcing concept. */
    @Deprecated(forRemoval = true)
    default CellVolumeRate evaporativeDemandAt(int x, int y) {
        return evaporativeDemandNormalAt(x, y);
    }

    default boolean contains(int x, int y) {
        WorldBounds bounds = bounds();
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }
}
