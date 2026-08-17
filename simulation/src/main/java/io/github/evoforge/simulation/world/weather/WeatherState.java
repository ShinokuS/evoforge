package io.github.evoforge.simulation.world.weather;

import io.github.evoforge.simulation.world.climate.ClimateNormalsField;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;

/**
 * Authoritative mutable current-weather state by global XY column.
 *
 * <p>This is runtime state, not a generated climate fact. Climate normals may initialize a neutral
 * starting atmosphere, while a weather driver is responsible for subsequent event evolution.
 * This class intentionally contains no rain probability, event cadence, season rule or balancing
 * coefficient.</p>
 */
public final class WeatherState {
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
     * Initializes each column at its generated long-term mean temperature with no instantaneous
     * precipitation or evaporative forcing. This is an initial condition, not a weather model.
     */
    public static WeatherState calmFromClimateNormals(ClimateNormalsField climate) {
        if (climate == null) {
            throw new IllegalArgumentException("climate normals must not be null");
        }
        WorldBounds bounds = climate.bounds();
        WeatherState state = new WeatherState(
                bounds,
                WeatherCellState.calm(climate.meanTemperatureAt(bounds.minX(), bounds.minY())
                        .asAirTemperature()));
        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            int worldY = (int) y;
            for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                int worldX = (int) x;
                state.setAt(
                        worldX,
                        worldY,
                        WeatherCellState.calm(
                                climate.meanTemperatureAt(worldX, worldY).asAirTemperature()));
            }
        }
        return state;
    }

    public WorldBounds bounds() {
        return bounds;
    }

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

    public boolean contains(int x, int y) {
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
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
