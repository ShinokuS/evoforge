package io.github.evoforge.simulation.world.weather;

import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.environment.atmosphere.AtmosphericWaterForcing;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRateIntegrator;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRateCellVolumeCompiler;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Runtime atmospheric-Water forcing backed by authoritative current WeatherState. */
public final class WeatherWaterForcing implements AtmosphericWaterForcing {
    private final WeatherState weather;
    private final PhysicalSpaceScale spaceScale;
    private final SimulationTimeScale timeScale;
    private final WeatherDriver driver;
    private final int width;
    private final CellVolumeRateIntegrator[] precipitationIntegrators;
    private final CellVolumeRateIntegrator[] evaporationIntegrators;
    private final long[] precipitationDue;
    private final long[] evaporationDue;
    private long lastIntegratedTick;

    public WeatherWaterForcing(
            WeatherState weather,
            PhysicalSpaceScale spaceScale,
            SimulationTimeScale timeScale) {
        this(weather, spaceScale, timeScale, WeatherDriver.stationary());
    }

    public WeatherWaterForcing(
            WeatherState weather,
            PhysicalSpaceScale spaceScale,
            SimulationTimeScale timeScale,
            WeatherDriver driver) {
        if (weather == null || spaceScale == null || timeScale == null || driver == null) {
            throw new IllegalArgumentException("weather forcing dependencies must not be null");
        }
        this.weather = weather;
        this.spaceScale = spaceScale;
        this.timeScale = timeScale;
        this.driver = driver;
        WorldBounds bounds = weather.bounds();
        width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.multiplyExact(width, height);
        precipitationIntegrators = new CellVolumeRateIntegrator[area];
        evaporationIntegrators = new CellVolumeRateIntegrator[area];
        precipitationDue = new long[area];
        evaporationDue = new long[area];
    }

    @Override public WorldBounds bounds() { return weather.bounds(); }

    public CellVolumeRate precipitationRateAt(int x, int y) {
        return WaterDepthRateCellVolumeCompiler.compile(
                weather.at(x, y).precipitationRate(), spaceScale, timeScale);
    }

    public CellVolumeRate evaporativeDemandRateAt(int x, int y) {
        return WaterDepthRateCellVolumeCompiler.compile(
                weather.at(x, y).evaporativeDemandRate(), spaceScale, timeScale);
    }

    @Override
    public void advanceToTick(long tick) {
        if (tick <= 0L) throw new IllegalArgumentException("weather forcing tick must be positive");
        long expected = Math.addExact(lastIntegratedTick, 1L);
        if (tick != expected) {
            throw new IllegalStateException(
                    "dynamic weather forcing must advance sequentially: expected="
                            + expected + ", actual=" + tick);
        }
        driver.update(tick);
        WorldBounds bounds = bounds();
        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            int worldY = (int) y;
            for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                int worldX = (int) x;
                int index = indexOf(worldX, worldY);
                precipitationDue[index] = integrator(precipitationIntegrators, index)
                        .advance(precipitationRateAt(worldX, worldY));
                evaporationDue[index] = integrator(evaporationIntegrators, index)
                        .advance(evaporativeDemandRateAt(worldX, worldY));
            }
        }
        lastIntegratedTick = tick;
    }

    @Override
    public long precipitationDueAt(int x, int y) {
        requireAdvanced();
        return precipitationDue[indexOf(x, y)];
    }

    @Override
    public long evaporativeDemandDueAt(int x, int y) {
        requireAdvanced();
        return evaporationDue[indexOf(x, y)];
    }

    private void requireAdvanced() {
        if (lastIntegratedTick <= 0L) {
            throw new IllegalStateException("weather forcing must advance before interval amounts are read");
        }
    }

    private CellVolumeRateIntegrator integrator(CellVolumeRateIntegrator[] integrators, int index) {
        CellVolumeRateIntegrator current = integrators[index];
        if (current == null) {
            current = new CellVolumeRateIntegrator();
            integrators[index] = current;
        }
        return current;
    }

    private int indexOf(int x, int y) {
        if (!weather.contains(x, y)) {
            throw new IllegalArgumentException(
                    "position outside weather forcing field: (" + x + ", " + y + ")");
        }
        WorldBounds bounds = bounds();
        int localX = x - bounds.minX();
        int localY = y - bounds.minY();
        return Math.addExact(Math.multiplyExact(localY, width), localX);
    }
}
