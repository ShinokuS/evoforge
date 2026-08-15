package io.github.evoforge.simulation.world.landscape.water;

import io.github.evoforge.simulation.world.landscape.liquid.LiquidFlowSample;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidFlowSystem;
import io.github.evoforge.simulation.world.landscape.liquid.StandardLiquidTypes;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;

/**
 * Water-specific facade over the shared deterministic free-liquid solver.
 *
 * <p>Hydrology and presentation can keep Water-shaped capabilities while the
 * transport algorithm itself is reusable by any liquid type stored in the same
 * authoritative {@code LiquidSystem}.
 */
public final class WaterFlowSystem {

    private final LiquidFlowSystem flow;
    private final WaterFlowLookup flowLookup;

    public WaterFlowSystem(
            WaterSystem water,
            GeometryLookup geometry) {
        this(water, geometry, SurfaceWaterStorageLookup.NONE);
    }

    public WaterFlowSystem(
            WaterSystem water,
            GeometryLookup geometry,
            SurfaceWaterStorageLookup surfaceStorage) {

        if (water == null || geometry == null || surfaceStorage == null) {
            throw new IllegalArgumentException(
                    "water flow dependencies must not be null");
        }

        flow = new LiquidFlowSystem(
                water.liquidSystem(),
                geometry,
                (type, x, y, z) -> StandardLiquidTypes.WATER.equals(type)
                        ? surfaceStorage.capacityAtWaterCell(x, y, z)
                        : 0);
        flowLookup = (x, y, z) -> {
            LiquidFlowSample sample = flow.flowLookup().find(x, y, z);
            if (sample == null || !StandardLiquidTypes.WATER.equals(sample.type())) {
                return null;
            }
            return new WaterFlowSample(
                    sample.dx(),
                    sample.dy(),
                    sample.dz(),
                    sample.amount());
        };
    }

    /** Actual sparse Water transfer state from the latest shared solver step. */
    public WaterFlowLookup flowLookup() {
        return flowLookup;
    }

    public long update() {
        return flow.update();
    }

    public void activateAt(int x, int y, int z) {
        flow.activateAt(x, y, z);
    }

    public int activeCellCount() {
        return flow.activeCellCount();
    }
}
