package io.github.evoforge.simulation.world.landscape.soil;

import io.github.evoforge.simulation.world.landscape.liquid.LiquidTypeId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/**
 * Typed compatibility facade over retained Soil liquid composition.
 *
 * <p>Existing hydrology/presentation consumers can continue to reason about one
 * moisture constituent while {@link SoilLiquidSystem} owns the shared pore volume
 * and may retain other liquid types independently.
 */
public final class SoilMoistureSystem {

    private static final LiquidTypeId LEGACY_MOISTURE_TYPE =
            LiquidTypeId.of("water");

    private final SoilLiquidSystem liquids;
    private final LiquidTypeId moistureType;
    private final SoilMoistureLookup lookup;
    private final SoilMoistureCellsLookup cells;

    /** Creates a typed moisture projection over a shared retained-liquid owner. */
    public SoilMoistureSystem(
            SoilLiquidSystem liquids,
            LiquidTypeId moistureType) {

        if (liquids == null || moistureType == null) {
            throw new IllegalArgumentException(
                    "Soil moisture dependencies must not be null");
        }
        this.liquids = liquids;
        this.moistureType = moistureType;
        lookup = (x, y, z) -> liquids.lookup().amountOf(
                moistureType, x, y, z);
        cells = new SoilMoistureCellsLookup() {
            @Override
            public int wetCellCount() {
                return liquids.cells().cellCount(moistureType);
            }

            @Override
            public void forEach(SoilMoistureCellConsumer consumer) {
                if (consumer == null) {
                    throw new IllegalArgumentException(
                            "consumer must not be null");
                }
                liquids.cells().forEach(
                        moistureType,
                        (x, y, z) -> consumer.accept(x, y, z));
            }
        };
    }

    /** Backward-compatible Water-only composition without coordinate-local variation. */
    public SoilMoistureSystem(
            SoilMoistureStorage storage,
            TerrainLookup terrain,
            SoilHydrologyDefinitions hydrology) {
        this(
                storage,
                new TerrainSoilHydrologyLookup(
                        terrain,
                        hydrology));
    }

    /**
     * Backward-compatible Water-only composition for existing fixtures.
     * New multi-liquid composition should create one shared {@link SoilLiquidSystem}
     * and pass an explicit liquid identity to the primary constructor.
     */
    public SoilMoistureSystem(
            SoilMoistureStorage storage,
            SoilHydrologyLookup hydrology) {
        this(
                new SoilLiquidSystem(
                        new MoistureStorageAdapter(
                                requireStorage(storage),
                                LEGACY_MOISTURE_TYPE),
                        requireHydrology(hydrology)),
                LEGACY_MOISTURE_TYPE);
    }

    public SoilMoistureLookup lookup() {
        return lookup;
    }

    public SoilMoistureCellsLookup cells() {
        return cells;
    }

    /** Returns the effective local material hydrology, or null for non-absorbing terrain. */
    public SoilHydrology hydrologyAt(int x, int y, int z) {
        return liquids.hydrologyAt(x, y, z);
    }

    public int infiltrateAtMost(
            int x,
            int y,
            int z,
            int requested) {
        return liquids.infiltrateAtMost(
                moistureType,
                x,
                y,
                z,
                requested);
    }

    public int removeAtMost(
            int x,
            int y,
            int z,
            int requested) {
        return liquids.removeAtMost(
                moistureType,
                x,
                y,
                z,
                requested);
    }

    private static SoilMoistureStorage requireStorage(SoilMoistureStorage storage) {
        if (storage == null) {
            throw new IllegalArgumentException("storage must not be null");
        }
        return storage;
    }

    private static SoilHydrologyLookup requireHydrology(SoilHydrologyLookup hydrology) {
        if (hydrology == null) {
            throw new IllegalArgumentException("hydrology must not be null");
        }
        return hydrology;
    }

    private static final class MoistureStorageAdapter
            implements SoilLiquidStorage {
        private final SoilMoistureStorage storage;
        private final LiquidTypeId type;

        private MoistureStorageAdapter(
                SoilMoistureStorage storage,
                LiquidTypeId type) {
            this.storage = storage;
            this.type = type;
        }

        @Override
        public int amountOf(
                LiquidTypeId requestedType,
                int x,
                int y,
                int z) {
            return type.equals(requestedType)
                    ? CellVolume.requireValid(storage.amount(x, y, z))
                    : CellVolume.EMPTY;
        }

        @Override
        public int totalAmount(int x, int y, int z) {
            return CellVolume.requireValid(storage.amount(x, y, z));
        }

        @Override
        public void put(
                int x,
                int y,
                int z,
                LiquidTypeId requestedType,
                int amount) {
            requireProjectedType(requestedType);
            storage.put(x, y, z, amount);
        }

        @Override
        public void remove(
                int x,
                int y,
                int z,
                LiquidTypeId requestedType) {
            requireProjectedType(requestedType);
            storage.remove(x, y, z);
        }

        private void requireProjectedType(LiquidTypeId requestedType) {
            if (!type.equals(requestedType)) {
                throw new IllegalArgumentException(
                        "legacy Soil moisture storage cannot retain "
                                + requestedType);
            }
        }
    }
}
