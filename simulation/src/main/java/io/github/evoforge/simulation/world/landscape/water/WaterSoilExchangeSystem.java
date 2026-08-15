package io.github.evoforge.simulation.world.landscape.water;

import io.github.evoforge.simulation.world.landscape.liquid.LiquidFlowPreparation;
import io.github.evoforge.simulation.world.landscape.soil.SoilLiquidInfiltrationSystem;
import io.github.evoforge.simulation.world.landscape.soil.SoilMoistureSystem;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;

/**
 * Compatibility adapter for the current Water-oriented hydrology composition.
 *
 * <p>The actual free-liquid -> Soil mechanism is generic and iterates every active
 * liquid identity. This adapter remains only because the current production
 * composition is still entered through Water-shaped capabilities.
 */
public final class WaterSoilExchangeSystem implements LiquidFlowPreparation {

    private final SoilLiquidInfiltrationSystem delegate;

    public WaterSoilExchangeSystem(
            WaterSystem water,
            TerrainLookup terrain,
            SoilMoistureSystem soil) {

        if (water == null || terrain == null || soil == null) {
            throw new IllegalArgumentException(
                    "water-soil exchange dependencies must not be null");
        }
        delegate = new SoilLiquidInfiltrationSystem(
                water.liquidSystem(),
                terrain,
                (type, x, y, z, requested) ->
                        soil.infiltrateAtMost(type, x, y, z, requested));
    }

    /** Runs one generic active-liquid infiltration pass before shared flow. */
    public long update() {
        return delegate.update();
    }

    @Override
    public void prepare() {
        update();
    }
}
