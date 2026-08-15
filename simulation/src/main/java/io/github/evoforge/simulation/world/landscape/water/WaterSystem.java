package io.github.evoforge.simulation.world.landscape.water;

import java.util.ArrayList;
import java.util.List;

import io.github.evoforge.simulation.world.landscape.liquid.LiquidStorage;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidSystem;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidTypeId;
import io.github.evoforge.simulation.world.landscape.liquid.StandardLiquidTypes;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;

/**
 * Water-specific capability facade over the shared free-liquid owner.
 *
 * <p>Water hydrology consumers keep a narrow Water API while transport/storage
 * mechanics live in {@link LiquidSystem}. This prevents rain, Soil and traversal
 * semantics from becoming implicit rules for every future liquid type.
 */
public final class WaterSystem {

    private static final LiquidTypeId WATER = StandardLiquidTypes.WATER;

    private final LiquidSystem liquids;
    private final WaterLookup lookup;
    private final WaterSurfaceLookup surfaces;

    public WaterSystem(LiquidSystem liquids) {
        if (liquids == null) {
            throw new IllegalArgumentException("liquids must not be null");
        }
        this.liquids = liquids;
        lookup = (x, y, z) -> liquids.lookup().amountOf(WATER, x, y, z);
        surfaces = new WaterSurfaceLookup() {
            @Override
            public boolean hasColumn(int x, int y) {
                return liquids.surfaces().hasColumn(WATER, x, y);
            }

            @Override
            public int topZ(int x, int y) {
                return liquids.surfaces().topZ(WATER, x, y);
            }

            @Override
            public int columnCount() {
                return liquids.surfaces().columnCount(WATER);
            }

            @Override
            public void forEach(WaterSurfaceConsumer consumer) {
                if (consumer == null) {
                    throw new IllegalArgumentException("consumer must not be null");
                }
                liquids.surfaces().forEach(
                        WATER,
                        (x, y, z, type) -> consumer.accept(x, y, z));
            }
        };
    }

    /**
     * Compatibility constructor for narrow Water fixtures. Production composition
     * owns one shared {@link LiquidSystem} and uses {@link #WaterSystem(LiquidSystem)}.
     */
    public WaterSystem(
            WaterStorage storage,
            GeometryLookup geometry) {
        this(new LiquidSystem(new WaterStorageAdapter(storage), geometry));
    }

    public WaterLookup lookup() {
        return lookup;
    }

    public WaterSurfaceLookup surfaces() {
        return surfaces;
    }

    public int addAtMost(int x, int y, int z, int requested) {
        return liquids.addAtMost(WATER, x, y, z, requested);
    }

    public int removeAtMost(int x, int y, int z, int requested) {
        return liquids.removeAtMost(WATER, x, y, z, requested);
    }

    LiquidSystem liquidSystem() {
        return liquids;
    }

    List<WaterCell> activeCellsSorted() {
        List<WaterCell> active = new ArrayList<>();
        liquids.forEachActive(
                WATER,
                (x, y, z) -> active.add(new WaterCell(x, y, z)));
        return active;
    }

    private static final class WaterStorageAdapter implements LiquidStorage {
        private final WaterStorage storage;

        private WaterStorageAdapter(WaterStorage storage) {
            if (storage == null) {
                throw new IllegalArgumentException("storage must not be null");
            }
            this.storage = storage;
        }

        @Override
        public LiquidTypeId typeAt(int x, int y, int z) {
            return storage.amount(x, y, z) > 0 ? WATER : null;
        }

        @Override
        public int amount(int x, int y, int z) {
            return storage.amount(x, y, z);
        }

        @Override
        public void put(
                int x,
                int y,
                int z,
                LiquidTypeId type,
                int amount) {
            if (!WATER.equals(type)) {
                throw new IllegalArgumentException(
                        "Water storage adapter cannot store " + type);
            }
            storage.put(x, y, z, amount);
        }

        @Override
        public void remove(int x, int y, int z) {
            storage.remove(x, y, z);
        }
    }
}
