package io.github.evoforge.simulation.world.environment.atmosphere;

import io.github.evoforge.simulation.world.environment.evaporation.EvaporationBatchResult;
import io.github.evoforge.simulation.world.environment.precipitation.PrecipitationBatchResult;

/** Exact accounting for one realized atmospheric Water-forcing interval. */
public record AtmosphericWaterForcingResult(
        EvaporationBatchResult evaporation,
        PrecipitationBatchResult precipitation) {

    public AtmosphericWaterForcingResult {
        if (evaporation == null || precipitation == null) {
            throw new IllegalArgumentException("forcing results must not be null");
        }
    }
}
