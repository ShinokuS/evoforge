package io.github.evoforge.simulation.world.spatial;

import io.github.evoforge.simulation.world.object.ObjectId;

import java.util.Arrays;

final class TransformState implements TransformLookup {

    private static final int INITIAL_CAPACITY = 16;

    private double[] x = new double[INITIAL_CAPACITY];

    private double[] y = new double[INITIAL_CAPACITY];

    private double[] z = new double[INITIAL_CAPACITY];

    private int[] generations = new int[INITIAL_CAPACITY];

    private boolean[] present = new boolean[INITIAL_CAPACITY];

    @Override
    public boolean has(ObjectId id) {
        if (id == null) {
            return false;
        }

        int slot = id.slot();

        return slot < present.length
                && present[slot]
                && generations[slot] == id.generation();
    }

    @Override
    public double x(ObjectId id) {
        requirePresent(id);
        return x[id.slot()];
    }

    @Override
    public double y(ObjectId id) {
        requirePresent(id);
        return y[id.slot()];
    }

    @Override
    public double z(ObjectId id) {
        requirePresent(id);
        return z[id.slot()];
    }

    void add(
            ObjectId id,
            double x,
            double y,
            double z) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "id must not be null");
        }

        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");

        ensureCapacity(id.slot());

        if (present[id.slot()]) {
            throw new IllegalStateException(
                    "transform already exists for slot: "
                            + id.slot());
        }

        int slot = id.slot();

        this.x[slot] = x;
        this.y[slot] = y;
        this.z[slot] = z;
        generations[slot] = id.generation();
        present[slot] = true;
    }

    void move(
            ObjectId id,
            double x,
            double y,
            double z) {

        requirePresent(id);

        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");

        int slot = id.slot();

        this.x[slot] = x;
        this.y[slot] = y;
        this.z[slot] = z;
    }

    void remove(ObjectId id) {
        requirePresent(id);

        present[id.slot()] = false;
    }

    private void requirePresent(ObjectId id) {
        if (!has(id)) {
            throw new IllegalStateException(
                    "transform does not exist: " + id);
        }
    }

    private void ensureCapacity(int slot) {
        if (slot < present.length) {
            return;
        }

        int capacity = Math.max(
                slot + 1,
                present.length * 2);

        x = Arrays.copyOf(x, capacity);
        y = Arrays.copyOf(y, capacity);
        z = Arrays.copyOf(z, capacity);
        generations = Arrays.copyOf(
                generations,
                capacity);
        present = Arrays.copyOf(
                present,
                capacity);
    }

    private static void requireFinite(
            double value,
            String name) {

        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    name + " must be finite");
        }
    }
}