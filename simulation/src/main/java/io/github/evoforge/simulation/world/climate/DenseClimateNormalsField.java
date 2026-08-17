package io.github.evoforge.simulation.world.climate;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable V5+ climate facts: spatial temperature plus compact uniform water normals. */
public final class DenseClimateNormalsField implements ClimateNormalsField {
    private final WorldBounds bounds;
    private final int width;
    private final int[] meanTemperatureMilliCelsius;
    private final ClimateWaterNormal precipitationNormal;
    private final ClimateWaterNormal evaporativeDemandNormal;

    public DenseClimateNormalsField(
            WorldBounds bounds,
            int[] meanTemperatureMilliCelsius,
            CellVolumeRate precipitationNormal,
            CellVolumeRate evaporativeDemandNormal) {
        this(
                bounds,
                meanTemperatureMilliCelsius,
                ClimateWaterNormal.legacy(precipitationNormal),
                ClimateWaterNormal.legacy(evaporativeDemandNormal));
    }

    public DenseClimateNormalsField(
            WorldBounds bounds,
            int[] meanTemperatureMilliCelsius,
            ClimateWaterNormal precipitationNormal,
            ClimateWaterNormal evaporativeDemandNormal) {
        if (bounds == null
                || meanTemperatureMilliCelsius == null
                || precipitationNormal == null
                || evaporativeDemandNormal == null) {
            throw new IllegalArgumentException("climate normals field inputs must not be null");
        }
        if (!precipitationNormal.kind().equals(evaporativeDemandNormal.kind())) {
            throw new IllegalArgumentException("climate water normals must use one shared dimension");
        }
        this.bounds = bounds;
        width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.multiplyExact(width, height);
        if (meanTemperatureMilliCelsius.length != area) {
            throw new IllegalArgumentException("temperature array must match world XY area");
        }
        this.meanTemperatureMilliCelsius = meanTemperatureMilliCelsius.clone();
        for (int value : this.meanTemperatureMilliCelsius) {
            ClimateTemperature.ofMilliCelsius(value);
        }
        this.precipitationNormal = precipitationNormal;
        this.evaporativeDemandNormal = evaporativeDemandNormal;
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public ClimateTemperature meanTemperatureAt(int x, int y) {
        return ClimateTemperature.ofMilliCelsius(meanTemperatureMilliCelsius[indexOf(x, y)]);
    }

    @Override
    public ClimateWaterNormal.Kind waterNormalKind() {
        return precipitationNormal.kind();
    }

    @Override
    public ClimateWaterNormal precipitationWaterNormalAt(int x, int y) {
        requireContains(x, y);
        return precipitationNormal;
    }

    @Override
    public ClimateWaterNormal evaporativeDemandWaterNormalAt(int x, int y) {
        requireContains(x, y);
        return evaporativeDemandNormal;
    }

    private int indexOf(int x, int y) {
        requireContains(x, y);
        int localX = x - bounds.minX();
        int localY = y - bounds.minY();
        return Math.addExact(Math.multiplyExact(localY, width), localX);
    }

    private void requireContains(int x, int y) {
        if (!contains(x, y)) {
            throw new IllegalArgumentException(
                    "position outside climate normals field: (" + x + ", " + y + ")");
        }
    }
}
