package io.github.evoforge.simulation.world.environment.climate;

import java.util.HashMap;
import java.util.Map;

import io.github.evoforge.simulation.world.atlas.HydroClimateField;
import io.github.evoforge.simulation.world.environment.evaporation.EvaporationBatchResult;
import io.github.evoforge.simulation.world.environment.evaporation.EvaporationSystem;
import io.github.evoforge.simulation.world.environment.precipitation.PrecipitationBatchResult;
import io.github.evoforge.simulation.world.environment.precipitation.SkyPrecipitationSystem;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;

/**
 * Realizes generated hydrologic climate normals through existing atmospheric Water mechanics.
 *
 * <p>This adapter owns no Water, Soil or weather state. It converts immutable Atlas rates into
 * exact per-tick source/sink requests and delegates all mutation to the existing evaporation and
 * precipitation systems.
 *
 * <p>Potential evaporation is evaluated against state present at the start of the interval; the
 * precipitation supply for that interval is then added at its boundary. This deterministic
 * discretization prevents newly supplied rain from being immediately removed by the same
 * baseline-climate tick. Eventful weather remains a separate future concern.
 */
public final class HydroClimateForcingSystem {

    private final HydroClimateField climate;
    private final EvaporationSystem evaporation;
    private final SkyPrecipitationSystem precipitation;

    public HydroClimateForcingSystem(
            HydroClimateField climate,
            EvaporationSystem evaporation,
            SkyPrecipitationSystem precipitation) {
        if (climate == null || evaporation == null || precipitation == null) {
            throw new IllegalArgumentException(
                    "hydro-climate forcing dependencies must not be null");
        }
        this.climate = climate;
        this.evaporation = evaporation;
        this.precipitation = precipitation;
    }

    /** Realizes exactly one positive absolute simulation tick of baseline forcing. */
    public HydroClimateForcingResult update(long tick) {
        if (tick <= 0L) {
            throw new IllegalArgumentException("tick must be positive");
        }

        Map<CellVolumeRate, Long> dueByRate = new HashMap<>();

        EvaporationBatchResult evaporationResult = evaporation.applyByColumn(
                (x, y) -> due(
                        climate.potentialEvaporationAt(x, y),
                        tick,
                        dueByRate));

        PrecipitationBatchResult precipitationResult = precipitation.applyByColumn(
                (x, y) -> due(
                        climate.precipitationSupplyAt(x, y),
                        tick,
                        dueByRate));

        return new HydroClimateForcingResult(
                evaporationResult,
                precipitationResult);
    }

    private static long due(
            CellVolumeRate rate,
            long tick,
            Map<CellVolumeRate, Long> dueByRate) {
        if (rate == null) {
            throw new IllegalStateException(
                    "hydro-climate field returned null rate");
        }
        return dueByRate.computeIfAbsent(
                rate,
                ignored -> rate.volumeDueAtTick(tick));
    }
}
