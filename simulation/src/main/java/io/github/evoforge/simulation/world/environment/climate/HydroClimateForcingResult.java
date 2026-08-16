package io.github.evoforge.simulation.world.environment.climate;

import io.github.evoforge.simulation.world.environment.evaporation.EvaporationBatchResult;
import io.github.evoforge.simulation.world.environment.precipitation.PrecipitationBatchResult;

/** Exact accounting for one realized hydrologic-climate tick. */
public record HydroClimateForcingResult(
        EvaporationBatchResult evaporation,
        PrecipitationBatchResult precipitation) {

    public HydroClimateForcingResult {
        if (evaporation == null || precipitation == null) {
            throw new IllegalArgumentException(
                    "forcing results must not be null");
        }
    }
}
