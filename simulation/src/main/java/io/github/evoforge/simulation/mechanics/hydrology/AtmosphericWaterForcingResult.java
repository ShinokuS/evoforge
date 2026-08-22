package io.github.evoforge.simulation.mechanics.hydrology;

import io.github.evoforge.simulation.mechanics.hydrology.EvaporationBatchResult;
import io.github.evoforge.simulation.mechanics.hydrology.PrecipitationBatchResult;

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
