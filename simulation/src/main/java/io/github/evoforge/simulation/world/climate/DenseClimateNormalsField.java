package io.github.evoforge.simulation.world.climate;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Dense immutable generated climate facts over one finite world XY area. */
public final class DenseClimateNormalsField implements ClimateNormalsField {
    private final WorldBounds bounds;
    private final int width;
    private final int[] meanTemperatureMilliCelsius;
    private final CellVolumeRate[] precipitationSupply;
    private final CellVolumeRate[] evaporativeDemand;

    public DenseClimateNormalsField(
            WorldBounds bounds,
            int[] meanTemperatureMilliCelsius,
            CellVolumeRate[] precipitationSupply,
            CellVolumeRate[] evaporativeDemand) {
        if (bounds == null
                || meanTemperatureMilliCelsius == null
                || precipitationSupply == null
                || evaporativeDemand == null) {
            throw new IllegalArgumentException("climate normals field inputs must not be null");
        }
        this.bounds = bounds;
        width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.multiplyExact(width, height);
        if (meanTemperatureMilliCelsius.length != area
                || precipitationSupply.length != area
                || evaporativeDemand.length != area) {
            throw new IllegalArgumentException("climate normals arrays must match world XY area");
        }
        this.meanTemperatureMilliCelsius = meanTemperatureMilliCelsius.clone();
        this.precipitationSupply = precipitationSupply.clone();
        this.evaporativeDemand = evaporativeDemand.clone();
        for (int index = 0; index < area; index++) {
            ClimateTemperature.ofMilliCelsius(this.meanTemperatureMilliCelsius[index]);
            if (this.precipitationSupply[index] == null || this.evaporativeDemand[index] == null) {
                throw new IllegalArgumentException("climate hydrologic rates must not be null");
            }
        }
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
    public CellVolumeRate precipitationSupplyAt(int x, int y) {
        return precipitationSupply[indexOf(x, y)];
    }

    @Override
    public CellVolumeRate evaporativeDemandAt(int x, int y) {
        return evaporativeDemand[indexOf(x, y)];
    }

    private int indexOf(int x, int y) {
        if (!contains(x, y)) {
            throw new IllegalArgumentException(
                    "position outside climate normals field: (" + x + ", " + y + ")");
        }
        int localX = x - bounds.minX();
        int localY = y - bounds.minY();
        return Math.addExact(Math.multiplyExact(localY, width), localX);
    }
}
