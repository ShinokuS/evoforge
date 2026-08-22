package io.github.evoforge.simulation.world.liquid.water;

import io.github.evoforge.simulation.world.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.liquid.LiquidTypeId;

/**
 * Water-specific capability facade over the shared authoritative free-liquid owner.
 *
 * <p>The facade owns only Water identity semantics. Storage, Geometry capacity,
 * hydraulic activity and transport belong to the generic liquid foundation.
 */
public final class WaterSystem {

    /** Open liquid identity owned by the Water domain, not a central liquid catalog. */
    public static final LiquidTypeId TYPE = LiquidTypeId.of("water");

    private final LiquidSystem liquids;
    private final WaterLookup lookup;
    private final WaterSurfaceLookup surfaces;

    public WaterSystem(LiquidSystem liquids) {
        if (liquids == null) {
            throw new IllegalArgumentException("liquids must not be null");
        }
        this.liquids = liquids;
        lookup = (x, y, z) -> liquids.lookup().amountOf(TYPE, x, y, z);
        surfaces = new WaterSurfaceLookup() {
            @Override
            public boolean hasColumn(int x, int y) {
                return liquids.surfaces().hasColumn(TYPE, x, y);
            }

            @Override
            public int topZ(int x, int y) {
                return liquids.surfaces().topZ(TYPE, x, y);
            }

            @Override
            public int columnCount() {
                return liquids.surfaces().columnCount(TYPE);
            }

            @Override
            public void forEach(WaterSurfaceConsumer consumer) {
                if (consumer == null) {
                    throw new IllegalArgumentException("consumer must not be null");
                }
                liquids.surfaces().forEach(
                        TYPE,
                        (x, y, z, type) -> consumer.accept(x, y, z));
            }
        };
    }

    public WaterLookup lookup() {
        return lookup;
    }

    public WaterSurfaceLookup surfaces() {
        return surfaces;
    }

    public int addAtMost(int x, int y, int z, int requested) {
        return liquids.addAtMost(TYPE, x, y, z, requested);
    }

    public int removeAtMost(int x, int y, int z, int requested) {
        return liquids.removeAtMost(TYPE, x, y, z, requested);
    }
}
