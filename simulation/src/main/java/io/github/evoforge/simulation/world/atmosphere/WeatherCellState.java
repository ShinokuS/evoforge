package io.github.evoforge.simulation.world.atmosphere;

import io.github.evoforge.simulation.world.atmosphere.AirTemperature;
import io.github.evoforge.simulation.world.atmosphere.WaterDepthRate;

/**
 * Current hydrologically relevant atmospheric state for one XY column.
 *
 * <p>All water fluxes are physical surface-depth rates and therefore remain independent from the
 * simulation cell size and tick duration. Additional weather dimensions such as humidity, wind
 * and radiation belong here only when downstream physics actually consumes them.</p>
 */
public record WeatherCellState(
        AirTemperature airTemperature,
        WaterDepthRate precipitationRate,
        WaterDepthRate evaporativeDemandRate) {

    public WeatherCellState {
        if (airTemperature == null || precipitationRate == null || evaporativeDemandRate == null) {
            throw new IllegalArgumentException("weather cell state components must not be null");
        }
    }

    public static WeatherCellState calm(AirTemperature airTemperature) {
        return new WeatherCellState(
                airTemperature,
                WaterDepthRate.ZERO,
                WaterDepthRate.ZERO);
    }
}
