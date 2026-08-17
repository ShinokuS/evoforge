package io.github.evoforge.simulation.world.environment.climate;

import java.util.HashMap;
import java.util.Map;

import io.github.evoforge.simulation.world.atlas.DynamicHydroClimateField;
import io.github.evoforge.simulation.world.atlas.HydroClimateField;
import io.github.evoforge.simulation.world.environment.evaporation.EvaporationBatchResult;
import io.github.evoforge.simulation.world.environment.evaporation.EvaporationSystem;
import io.github.evoforge.simulation.world.environment.precipitation.PrecipitationBatchResult;
import io.github.evoforge.simulation.world.environment.precipitation.SkyPrecipitationSystem;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;

/**
 * Realizes a hydrologic atmospheric forcing projection through existing Water mechanics.
 *
 * <p>This runtime adapter owns no climate facts, Water, Soil or weather state. Static generated
 * climate uses analytically tick-anchored rates; dynamic weather fields advance once at the start of
 * each interval and expose exact already-integrated whole-volume requests for that interval.</p>
 *
 * <p>Potential evaporation is evaluated against state present at the start of the interval; the
 * precipitation supply for that interval is then added at its boundary. This deterministic
 * discretization prevents newly supplied rain from being immediately removed by the same tick.</p>
 */
public final class HydroClimateForcingSystem {

    private final HydroClimateField forcing;
    private final EvaporationSystem evaporation;
    private final SkyPrecipitationSystem precipitation;

    public HydroClimateForcingSystem(
            HydroClimateField forcing,
            EvaporationSystem evaporation,
            SkyPrecipitationSystem precipitation) {
        if (forcing == null || evaporation == null || precipitation == null) {
            throw new IllegalArgumentException(
                    "hydro-climate forcing dependencies must not be null");
        }
        this.forcing = forcing;
        this.evaporation = evaporation;
        this.precipitation = precipitation;
    }

    /**
     * Realizes exactly one positive absolute simulation tick of atmospheric forcing.
     *
     * <p>This is an imperative mutation boundary, not an idempotent query. Runtime composition must
     * invoke it exactly once for every advanced simulation tick.</p>
     */
    public HydroClimateForcingResult update(long tick) {
        if (tick <= 0L) {
            throw new IllegalArgumentException("tick must be positive");
        }

        DynamicHydroClimateField dynamic = forcing instanceof DynamicHydroClimateField candidate
                ? candidate
                : null;
        if (dynamic != null) {
            dynamic.advanceToTick(tick);
        }

        Map<CellVolumeRate, Long> dueByRate = new HashMap<>();

        EvaporationBatchResult evaporationResult = evaporation.applyByColumn(
                (x, y) -> dynamic != null
                        ? dynamic.evaporativeDemandDueAt(x, y)
                        : due(forcing.evaporativeDemandAt(x, y), tick, dueByRate));

        PrecipitationBatchResult precipitationResult = precipitation.applyByColumn(
                (x, y) -> dynamic != null
                        ? dynamic.precipitationDueAt(x, y)
                        : due(forcing.precipitationSupplyAt(x, y), tick, dueByRate));

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
