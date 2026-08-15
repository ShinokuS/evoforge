package io.github.evoforge.simulation.world.landscape.liquid;

import java.util.List;

import io.github.evoforge.simulation.world.mechanics.geometry.CellSpace;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

/**
 * Authoritative owner of finite free-liquid quantity in shared world cells.
 *
 * <p>The current representation is deliberately single-component per occupied
 * cell. Adding a different liquid to an occupied cell is rejected by returning
 * zero; composition/mixing is a separate future responsibility rather than an
 * implicit overwrite or merge.
 */
public final class LiquidSystem {

    private final LiquidStorage storage;
    private final GeometryLookup geometry;
    private final LiquidFlowActivity flowActivity = new LiquidFlowActivity();
    private final LiquidSurfaceIndex surfaceIndex = new LiquidSurfaceIndex();
    private final LiquidLookup lookup = new LiquidLookup() {
        @Override
        public LiquidTypeId typeAt(int x, int y, int z) {
            return storage.typeAt(x, y, z);
        }

        @Override
        public int amount(int x, int y, int z) {
            return currentAmount(x, y, z);
        }
    };

    public LiquidSystem(
            LiquidStorage storage,
            GeometryLookup geometry) {

        if (storage == null) {
            throw new IllegalArgumentException("storage must not be null");
        }
        if (geometry == null) {
            throw new IllegalArgumentException("geometry must not be null");
        }
        this.storage = storage;
        this.geometry = geometry;
    }

    public LiquidLookup lookup() {
        return lookup;
    }

    public LiquidSurfaceLookup surfaces() {
        return surfaceIndex.lookup();
    }

    /**
     * Adds at most {@code requested} of one liquid and returns the actual amount.
     * A cell already occupied by another liquid accepts nothing until a future
     * composition/mixing policy explicitly defines that contact.
     */
    public int addAtMost(
            LiquidTypeId type,
            int x,
            int y,
            int z,
            int requested) {

        requireType(type);
        requireNonNegative(requested);
        if (requested == CellVolume.EMPTY) return CellVolume.EMPTY;

        LiquidTypeId resident = storage.typeAt(x, y, z);
        if (resident != null && !resident.equals(type)) {
            return CellVolume.EMPTY;
        }

        int current = currentAmount(x, y, z);
        int available = Math.max(CellVolume.EMPTY, capacity(x, y, z) - current);
        int added = Math.min(requested, available);
        if (added == CellVolume.EMPTY) return CellVolume.EMPTY;

        replaceStoredAmount(x, y, z, type, current + added);
        flowActivity.activate(x, y, z);
        return added;
    }

    /** Removes at most {@code requested} of the addressed liquid type. */
    public int removeAtMost(
            LiquidTypeId type,
            int x,
            int y,
            int z,
            int requested) {

        requireType(type);
        requireNonNegative(requested);
        if (requested == CellVolume.EMPTY) return CellVolume.EMPTY;
        if (!type.equals(storage.typeAt(x, y, z))) return CellVolume.EMPTY;

        int current = currentAmount(x, y, z);
        int removed = Math.min(requested, current);
        if (removed == CellVolume.EMPTY) return CellVolume.EMPTY;

        int remaining = current - removed;
        replaceStoredAmount(x, y, z, type, remaining);
        flowActivity.activate(x, y, z);
        return removed;
    }

    /** Iterates currently active cells containing {@code type} without draining them. */
    public void forEachActive(
            LiquidTypeId type,
            LiquidCellConsumer consumer) {

        requireType(type);
        if (consumer == null) {
            throw new IllegalArgumentException("consumer must not be null");
        }
        for (LiquidCell cell : flowActivity.snapshotSorted()) {
            if (type.equals(storage.typeAt(cell.x(), cell.y(), cell.z()))) {
                consumer.accept(cell.x(), cell.y(), cell.z());
            }
        }
    }

    LiquidFlowActivity flowActivity() {
        return flowActivity;
    }

    void replaceFromFlow(
            LiquidCell cell,
            LiquidTypeId type,
            int amount) {

        replaceStoredAmount(cell.x(), cell.y(), cell.z(), type, amount);
    }

    private void replaceStoredAmount(
            int x,
            int y,
            int z,
            LiquidTypeId type,
            int amount) {

        int validated = CellVolume.requireValid(amount);
        int previous = currentAmount(x, y, z);
        LiquidTypeId previousType = storage.typeAt(x, y, z);

        if (validated == CellVolume.EMPTY) {
            storage.remove(x, y, z);
        } else {
            requireType(type);
            if (previousType != null && !previousType.equals(type)) {
                throw new IllegalStateException(
                        "liquid flow attempted implicit mixing at ("
                                + x + ", " + y + ", " + z + "): "
                                + previousType + " + " + type);
            }
            storage.put(x, y, z, type, validated);
        }

        if (previous == CellVolume.EMPTY && validated > CellVolume.EMPTY) {
            surfaceIndex.becameWet(x, y, z, type);
        } else if (previous > CellVolume.EMPTY && validated == CellVolume.EMPTY) {
            surfaceIndex.becameDry(x, y, z);
        }
    }

    private int currentAmount(int x, int y, int z) {
        int amount = CellVolume.requireValid(storage.amount(x, y, z));
        LiquidTypeId type = storage.typeAt(x, y, z);
        if ((amount == CellVolume.EMPTY) != (type == null)) {
            throw new IllegalStateException(
                    "liquid storage type/amount invariant violated at ("
                            + x + ", " + y + ", " + z + ")");
        }
        return amount;
    }

    private int capacity(int x, int y, int z) {
        Shape shape = geometry.find(x, y, z);
        return CellSpace.capacity(shape);
    }

    private static void requireType(LiquidTypeId type) {
        if (type == null) {
            throw new IllegalArgumentException("liquid type must not be null");
        }
    }

    private static void requireNonNegative(int requested) {
        if (requested < CellVolume.EMPTY) {
            throw new IllegalArgumentException(
                    "requested liquid volume must not be negative: " + requested);
        }
        CellVolume.requireValid(requested);
    }
}
