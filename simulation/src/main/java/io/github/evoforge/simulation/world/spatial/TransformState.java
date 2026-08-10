package io.github.evoforge.simulation.world.spatial;

import io.github.evoforge.simulation.world.object.ObjectId;

import java.util.Arrays;

final class TransformState
        implements TransformLookup {

    private static final int INITIAL_CAPACITY = 16;

    private int[] x = new int[INITIAL_CAPACITY];

    private int[] y = new int[INITIAL_CAPACITY];

    private int[] z = new int[INITIAL_CAPACITY];

    private int[] generations = new int[INITIAL_CAPACITY];

    private boolean[] present = new boolean[INITIAL_CAPACITY];

    @Override
    public boolean has(
            ObjectId id) {

        if (id == null) {
            return false;
        }

        int slot = id.slot();

        return slot < present.length
                && present[slot]
                && generations[slot] == id.generation();
    }

    @Override
    public int x(
            ObjectId id) {

        return x[requirePresent(id)];
    }

    @Override
    public int y(
            ObjectId id) {

        return y[requirePresent(id)];
    }

    @Override
    public int z(
            ObjectId id) {

        return z[requirePresent(id)];
    }

    void add(
            ObjectId id,
            int x,
            int y,
            int z) {

        requireId(id);

        int slot = id.slot();

        ensureCapacity(slot);

        if (present[slot]) {
            throw new IllegalStateException(
                    "transform already exists: " + id);
        }

        this.x[slot] = x;
        this.y[slot] = y;
        this.z[slot] = z;

        generations[slot] = id.generation();

        present[slot] = true;
    }

    void move(
            ObjectId id,
            int x,
            int y,
            int z) {

        int slot = requirePresent(id);

        this.x[slot] = x;
        this.y[slot] = y;
        this.z[slot] = z;
    }

    void remove(
            ObjectId id) {

        int slot = requirePresent(id);

        present[slot] = false;
    }

    private int requirePresent(
            ObjectId id) {

        requireId(id);

        int slot = id.slot();

        if (slot >= present.length
                || !present[slot]
                || generations[slot] != id.generation()) {

            throw new IllegalStateException(
                    "transform does not exist: " + id);
        }

        return slot;
    }

    private void ensureCapacity(
            int slot) {

        if (slot < present.length) {
            return;
        }

        int newLength = present.length;

        while (newLength <= slot) {
            newLength = Math.max(
                    newLength * 2,
                    slot + 1);
        }

        x = Arrays.copyOf(
                x,
                newLength);

        y = Arrays.copyOf(
                y,
                newLength);

        z = Arrays.copyOf(
                z,
                newLength);

        generations = Arrays.copyOf(
                generations,
                newLength);

        present = Arrays.copyOf(
                present,
                newLength);
    }

    private static void requireId(
            ObjectId id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "id must not be null");
        }
    }
}