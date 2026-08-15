package io.github.evoforge.simulation.world.landscape.soil;

import java.util.TreeSet;

import io.github.evoforge.simulation.world.landscape.liquid.LiquidTypeId;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/**
 * Authoritative retained-liquid composition inside Soil pore volume.
 *
 * <p>Unlike free-liquid cells, retained Soil content may contain several liquid
 * constituents at once. They compete for one material-owned capacity; this is
 * retained composition, not a free-liquid mixing/chemistry model.
 */
public final class SoilLiquidSystem {

    private final SoilLiquidStorage storage;
    private final SoilHydrologyLookup hydrology;
    private final SoilLiquidInteractionLookup interactions;
    private final TreeSet<SoilCell> occupiedCells = new TreeSet<>();
    private final SoilLiquidLookup lookup = new SoilLiquidLookup() {
        @Override
        public int amountOf(
                LiquidTypeId type,
                int x,
                int y,
                int z) {
            requireType(type);
            return currentAmount(type, x, y, z);
        }

        @Override
        public int totalAmount(int x, int y, int z) {
            return currentTotal(x, y, z);
        }
    };
    private final SoilLiquidCellsLookup cells = new SoilLiquidCellsLookup() {
        @Override
        public int occupiedCellCount() {
            return occupiedCells.size();
        }

        @Override
        public int cellCount(LiquidTypeId type) {
            requireType(type);
            int count = 0;
            for (SoilCell cell : occupiedCells) {
                if (currentAmount(type, cell.x(), cell.y(), cell.z()) > CellVolume.EMPTY) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public void forEach(SoilLiquidCellConsumer consumer) {
            requireConsumer(consumer);
            for (SoilCell cell : occupiedCells) {
                consumer.accept(cell.x(), cell.y(), cell.z());
            }
        }

        @Override
        public void forEach(
                LiquidTypeId type,
                SoilLiquidCellConsumer consumer) {
            requireType(type);
            requireConsumer(consumer);
            for (SoilCell cell : occupiedCells) {
                if (currentAmount(type, cell.x(), cell.y(), cell.z()) > CellVolume.EMPTY) {
                    consumer.accept(cell.x(), cell.y(), cell.z());
                }
            }
        }
    };

    public SoilLiquidSystem(
            SoilLiquidStorage storage,
            SoilHydrologyLookup hydrology) {
        this(storage, hydrology, SoilLiquidInteractionLookup.DEFAULT);
    }

    public SoilLiquidSystem(
            SoilLiquidStorage storage,
            SoilHydrologyLookup hydrology,
            SoilLiquidInteractionLookup interactions) {

        if (storage == null || hydrology == null || interactions == null) {
            throw new IllegalArgumentException(
                    "Soil liquid dependencies must not be null");
        }
        this.storage = storage;
        this.hydrology = hydrology;
        this.interactions = interactions;
    }

    public SoilLiquidLookup lookup() {
        return lookup;
    }

    public SoilLiquidCellsLookup cells() {
        return cells;
    }

    /** Returns the effective local Soil hydrology, or null for non-absorbing terrain. */
    public SoilHydrology hydrologyAt(int x, int y, int z) {
        return hydrology.find(x, y, z);
    }

    /**
     * Retains at most one interaction-defined infiltration step while respecting
     * the one shared pore capacity across every retained liquid constituent.
     */
    public int infiltrateAtMost(
            LiquidTypeId type,
            int x,
            int y,
            int z,
            int requested) {

        requireType(type);
        requireNonNegative(requested);
        if (requested == CellVolume.EMPTY) return CellVolume.EMPTY;

        SoilHydrology localHydrology = hydrology.find(x, y, z);
        if (localHydrology == null) return CellVolume.EMPTY;

        int currentTotal = currentTotal(x, y, z);
        int available = Math.max(
                CellVolume.EMPTY,
                localHydrology.capacity() - currentTotal);
        if (available == CellVolume.EMPTY) return CellVolume.EMPTY;

        int interactionLimit = CellVolume.requireValid(
                interactions.infiltrationLimitAt(
                        type,
                        x,
                        y,
                        z,
                        localHydrology));
        int infiltrated = Math.min(
                requested,
                Math.min(available, interactionLimit));
        if (infiltrated == CellVolume.EMPTY) return CellVolume.EMPTY;

        int currentConstituent = currentAmount(type, x, y, z);
        storage.put(
                x,
                y,
                z,
                type,
                Math.addExact(currentConstituent, infiltrated));
        if (currentTotal == CellVolume.EMPTY) {
            occupiedCells.add(new SoilCell(x, y, z));
        }
        verifyCapacity(x, y, z, localHydrology);
        return infiltrated;
    }

    public int removeAtMost(
            LiquidTypeId type,
            int x,
            int y,
            int z,
            int requested) {

        requireType(type);
        requireNonNegative(requested);
        if (requested == CellVolume.EMPTY) return CellVolume.EMPTY;

        int current = currentAmount(type, x, y, z);
        int removed = Math.min(requested, current);
        if (removed == CellVolume.EMPTY) return CellVolume.EMPTY;

        int remaining = current - removed;
        if (remaining == CellVolume.EMPTY) {
            storage.remove(x, y, z, type);
        } else {
            storage.put(x, y, z, type, remaining);
        }

        if (currentTotal(x, y, z) == CellVolume.EMPTY) {
            occupiedCells.remove(new SoilCell(x, y, z));
        }
        return removed;
    }

    private int currentAmount(
            LiquidTypeId type,
            int x,
            int y,
            int z) {
        return CellVolume.requireValid(storage.amountOf(type, x, y, z));
    }

    private int currentTotal(int x, int y, int z) {
        int total = CellVolume.requireValid(storage.totalAmount(x, y, z));
        return total;
    }

    private void verifyCapacity(
            int x,
            int y,
            int z,
            SoilHydrology localHydrology) {
        int total = currentTotal(x, y, z);
        if (total > localHydrology.capacity()) {
            throw new IllegalStateException(
                    "retained Soil liquid exceeded pore capacity at ("
                            + x + ", " + y + ", " + z + "): "
                            + total + " > " + localHydrology.capacity());
        }
    }

    private static void requireType(LiquidTypeId type) {
        if (type == null) {
            throw new IllegalArgumentException("liquid type must not be null");
        }
    }

    private static void requireConsumer(SoilLiquidCellConsumer consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer must not be null");
        }
    }

    private static void requireNonNegative(int requested) {
        if (requested < CellVolume.EMPTY) {
            throw new IllegalArgumentException(
                    "requested retained liquid volume must not be negative: "
                            + requested);
        }
    }

    private record SoilCell(int x, int y, int z)
            implements Comparable<SoilCell> {
        @Override
        public int compareTo(SoilCell other) {
            int xOrder = Integer.compare(x, other.x);
            if (xOrder != 0) return xOrder;
            int yOrder = Integer.compare(y, other.y);
            return yOrder != 0 ? yOrder : Integer.compare(z, other.z);
        }
    }
}
