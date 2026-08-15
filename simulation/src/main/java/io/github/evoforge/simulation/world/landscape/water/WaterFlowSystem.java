package io.github.evoforge.simulation.world.landscape.water;

import io.github.evoforge.simulation.world.landscape.liquid.LiquidFlowSample;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidFlowSystem;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;

/**
 * Water-specific facade over the shared deterministic free-liquid solver.
 *
 * <p>Hydrology and presentation can keep Water-shaped capabilities while the
 * transport algorithm itself is reusable by every liquid type stored in the
 * same authoritative liquid owner.
 */
public final class WaterFlowSystem {

    private final LiquidFlowSystem flow;
    private final WaterFlowLookup flowLookup;

    /**
     * Wraps an already composed shared solver. This is the multi-liquid boundary:
     * typed facades observe one transport owner rather than creating parallel
     * solvers for each liquid identity.
     */
    public WaterFlowSystem(LiquidFlowSystem flow) {
        if (flow == null) {
            throw new IllegalArgumentException("liquid flow must not be null");
        }
        this.flow = flow;
        flowLookup = (x, y, z) -> {
            LiquidFlowSample sample = flow.flowLookup().find(x, y, z);
            if (sample == null || !WaterSystem.TYPE.equals(sample.type())) {
                return null;
            }
            return new WaterFlowSample(
                    sample.dx(),
                    sample.dy(),
                    sample.dz(),
                    sample.amount());
        };
    }

    /** Convenience composition for current Water-only fixtures. */
    public WaterFlowSystem(
            WaterSystem water,
            GeometryLookup geometry) {
        this(water, geometry, SurfaceWaterStorageLookup.NONE);
    }

    /**
     * Convenience composition for the current Water-only hydrology runtime.
     * A runtime containing several liquid identities should compose one shared
     * {@link LiquidFlowSystem} and use {@link #WaterFlowSystem(LiquidFlowSystem)}.
     */
    public WaterFlowSystem(
            WaterSystem water,
            GeometryLookup geometry,
            SurfaceWaterStorageLookup surfaceStorage) {

        this(createWaterOnlyFlow(water, geometry, surfaceStorage));
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

    LiquidFlowSystem liquidFlowSystem() {
        return flow;
    }

    private static LiquidFlowSystem createWaterOnlyFlow(
            WaterSystem water,
            GeometryLookup geometry,
            SurfaceWaterStorageLookup surfaceStorage) {

        if (water == null || geometry == null || surfaceStorage == null) {
            throw new IllegalArgumentException(
                    "water flow dependencies must not be null");
        }
        return new LiquidFlowSystem(
                water.liquidSystem(),
                geometry,
                (type, x, y, z) -> WaterSystem.TYPE.equals(type)
                        ? surfaceStorage.capacityAtWaterCell(x, y, z)
                        : 0);
    }
}
