package io.github.evoforge.simulation.world.environment.atmosphere;

import io.github.evoforge.simulation.world.environment.evaporation.EvaporationBatchResult;
import io.github.evoforge.simulation.world.environment.evaporation.EvaporationSystem;
import io.github.evoforge.simulation.world.environment.precipitation.PrecipitationBatchResult;
import io.github.evoforge.simulation.world.environment.precipitation.SkyPrecipitationSystem;

/**
 * Applies one already-composed atmospheric Water-forcing interval through existing Water mechanics.
 *
 * <p>This consumer owns no climate facts, WeatherState, random process, rate integration, Water or
 * Soil. The injected forcing advances itself and exposes whole source/sink amounts for the current
 * interval, so this consumer never branches on the concrete atmospheric model.</p>
 */
public final class AtmosphericWaterForcingSystem {
    private final AtmosphericWaterForcing forcing;
    private final EvaporationSystem evaporation;
    private final SkyPrecipitationSystem precipitation;

    public AtmosphericWaterForcingSystem(
            AtmosphericWaterForcing forcing,
            EvaporationSystem evaporation,
            SkyPrecipitationSystem precipitation) {
        if (forcing == null || evaporation == null || precipitation == null) {
            throw new IllegalArgumentException("atmospheric water forcing dependencies must not be null");
        }
        this.forcing = forcing;
        this.evaporation = evaporation;
        this.precipitation = precipitation;
    }

    /** Applies exactly one positive simulation interval in evaporation-then-precipitation order. */
    public AtmosphericWaterForcingResult update(long tick) {
        if (tick <= 0L) {
            throw new IllegalArgumentException("tick must be positive");
        }
        forcing.advanceToTick(tick);
        EvaporationBatchResult evaporationResult = evaporation.applyByColumn(
                forcing::evaporativeDemandDueAt);
        PrecipitationBatchResult precipitationResult = precipitation.applyByColumn(
                forcing::precipitationDueAt);
        return new AtmosphericWaterForcingResult(evaporationResult, precipitationResult);
    }
}
