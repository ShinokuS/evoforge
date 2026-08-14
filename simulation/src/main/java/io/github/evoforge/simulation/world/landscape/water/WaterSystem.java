package io.github.evoforge.simulation.world.landscape.water;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

public final class WaterSystem {

    private final WaterStorage storage;
    private final GeometryLookup geometry;
    private final WaterLookup lookup;

    public WaterSystem(
            WaterStorage storage,
            GeometryLookup geometry) {

        if (storage == null) {
            throw new IllegalArgumentException(
                    "storage must not be null");
        }
        if (geometry == null) {
            throw new IllegalArgumentException(
                    "geometry must not be null");
        }

        this.storage = storage;
        this.geometry = geometry;
        lookup = storage::amount;
    }

    public WaterLookup lookup() {
        return lookup;
    }

    /**
     * Adds no more than {@code requested} volume and returns the amount that
     * actually entered the cell. The operation never exceeds current geometric
     * free capacity.
     */
    public int addAtMost(
            int x,
            int y,
            int z,
            int requested) {

        requireNonNegative(requested);
        if (requested == CellVolume.EMPTY) {
            return CellVolume.EMPTY;
        }

        int current = currentAmount(x, y, z);
        int capacity = capacity(x, y, z);
        int available = Math.max(
                CellVolume.EMPTY,
                capacity - current);
        int added = Math.min(requested, available);

        if (added == CellVolume.EMPTY) {
            return CellVolume.EMPTY;
        }

        storage.put(
                x,
                y,
                z,
                current + added);
        return added;
    }

    /**
     * Removes no more than {@code requested} volume and returns the amount that
     * actually left the cell.
     */
    public int removeAtMost(
            int x,
            int y,
            int z,
            int requested) {

        requireNonNegative(requested);
        if (requested == CellVolume.EMPTY) {
            return CellVolume.EMPTY;
        }

        int current = currentAmount(x, y, z);
        int removed = Math.min(requested, current);
        if (removed == CellVolume.EMPTY) {
            return CellVolume.EMPTY;
        }

        int remaining = current - removed;
        if (remaining == CellVolume.EMPTY) {
            storage.remove(x, y, z);
        } else {
            storage.put(x, y, z, remaining);
        }

        return removed;
    }

    private int currentAmount(
            int x,
            int y,
            int z) {

        return CellVolume.requireValid(
                storage.amount(x, y, z));
    }

    private int capacity(
            int x,
            int y,
            int z) {

        Shape shape = geometry.find(x, y, z);
        if (shape == null) {
            return CellVolume.FULL;
        }

        int solid = CellVolume.requireValid(
                shape.solidVolume());
        return CellVolume.FULL - solid;
    }

    private static void requireNonNegative(
            int requested) {

        if (requested < CellVolume.EMPTY) {
            throw new IllegalArgumentException(
                    "requested water volume must not be negative: "
                            + requested);
        }
    }
}
