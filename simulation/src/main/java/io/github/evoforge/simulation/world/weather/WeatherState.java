package io.github.evoforge.simulation.world.weather;

import io.github.evoforge.simulation.world.climate.ClimateNormalsField;
import io.github.evoforge.simulation.world.climate.ClimateWaterNormal;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;

/**
 * Authoritative mutable current-weather state by global XY column.
 *
 * <p>This is runtime state, not a generated climate fact. Mutation is intended for simulation-owned
 * weather drivers; external runtime observation should use the {@link WeatherLookup} capability.</p>
 */
public final class WeatherState implements WeatherLookup {
    private final WorldBounds bounds;
    private final int width;
    private final WeatherCellState[] cells;

    public WeatherState(WorldBounds bounds, WeatherCellState initialState) {
        if (bounds == null || initialState == null) {
            throw new IllegalArgumentException("weather state inputs must not be null");
        }
        this.bounds = bounds;
        width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.multiplyExact(width, height);
        cells = new WeatherCellState[area];
        Arrays.fill(cells, initialState);
    }

    /**
     * Creates non-raining runtime weather from durable climate normals.
     *
     * <p>Physical V8+ climate keeps its potential evaporative demand as the initial current
     * atmospheric demand. Legacy cell-relative climate predates the physical weather boundary and
     * therefore retains the historical zero-demand behavior until explicitly adapted.</p>
     */
    public static WeatherState calmFromClimateNormals(ClimateNormalsField climate) {
        if (climate == null) {
            throw new IllegalArgumentException("climate normals must not be null");
        }
        WorldBounds bounds = climate.bounds();
        WeatherState state = new WeatherState(
                bounds,
                calmCellFromClimate(climate, bounds.minX(), bounds.minY()));
        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            int worldY = (int) y;
            for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                int worldX = (int) x;
                state.setAt(
                        worldX,
                        worldY,
                        calmCellFromClimate(climate, worldX, worldY));
            }
        }
        return state;
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public WeatherCellState at(int x, int y) {
        return cells[indexOf(x, y)];
    }

    public void setAt(int x, int y, WeatherCellState state) {
        if (state == null) {
            throw new IllegalArgumentException("weather cell state must not be null");
        }
        cells[indexOf(x, y)] = state;
    }

    /** Updates only current precipitation while preserving temperature and evaporation demand. */
    public void setPrecipitationRateAt(int x, int y, WaterDepthRate precipitationRate) {
        if (precipitationRate == null) {
            throw new IllegalArgumentException("precipitation rate must not be null");
        }
        WeatherCellState current = at(x, y);
        setAt(
                x,
                y,
                new WeatherCellState(
                        current.airTemperature(),
                        precipitationRate,
                        current.evaporativeDemandRate()));
    }

    @Override
    public boolean contains(int x, int y) {
        return WeatherLookup.super.contains(x, y);
    }

    private static WeatherCellState calmCellFromClimate(
            ClimateNormalsField climate,
            int x,
            int y) {
        WaterDepthRate evaporativeDemand = climate.waterNormalKind()
                == ClimateWaterNormal.Kind.PHYSICAL_WATER_DEPTH_PER_TIME
                ? climate.evaporativeDemandDepthNormalAt(x, y)
                : WaterDepthRate.ZERO;
        return new WeatherCellState(
                climate.meanTemperatureAt(x, y).asAirTemperature(),
                WaterDepthRate.ZERO,
                evaporativeDemand);
    }

    private int indexOf(int x, int y) {
        if (!contains(x, y)) {
            throw new IllegalArgumentException(
                    "position outside weather state: (" + x + ", " + y + ")");
        }
        int localX = x - bounds.minX();
        int localY = y - bounds.minY();
        return Math.addExact(Math.multiplyExact(localY, width), localX);
    }
}
