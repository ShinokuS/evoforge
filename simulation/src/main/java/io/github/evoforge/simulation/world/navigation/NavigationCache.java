package io.github.evoforge.simulation.world.navigation;

import java.util.Arrays;

final class NavigationCache {

    static final int MISS = Integer.MIN_VALUE;

    private static final byte EMPTY = 0;
    private static final byte OCCUPIED = 1;
    private static final byte DELETED = 2;

    private static final int INITIAL_CAPACITY = 16;
    private static final int LOAD_NUMERATOR = 7;
    private static final int LOAD_DENOMINATOR = 10;

    private int[] xs = new int[INITIAL_CAPACITY];
    private int[] ys = new int[INITIAL_CAPACITY];
    private int[] zs = new int[INITIAL_CAPACITY];
    private int[] values = new int[INITIAL_CAPACITY];
    private byte[] states = new byte[INITIAL_CAPACITY];

    private int size;
    private int used;
    private int threshold = threshold(INITIAL_CAPACITY);

    int get(
            int x,
            int y,
            int z) {

        int mask = states.length - 1;
        int index = hash(x, y, z) & mask;

        while (true) {
            byte state = states[index];

            if (state == EMPTY) {
                return MISS;
            }

            if (state == OCCUPIED
                    && xs[index] == x
                    && ys[index] == y
                    && zs[index] == z) {
                return values[index];
            }

            index = index + 1 & mask;
        }
    }

    void put(
            int x,
            int y,
            int z,
            int value) {

        if (value == MISS) {
            throw new IllegalArgumentException(
                    "reserved cache value");
        }

        ensureInsertCapacity();
        insert(x, y, z, value);
    }

    void remove(
            int x,
            int y,
            int z) {

        int mask = states.length - 1;
        int index = hash(x, y, z) & mask;

        while (true) {
            byte state = states[index];

            if (state == EMPTY) {
                return;
            }

            if (state == OCCUPIED
                    && xs[index] == x
                    && ys[index] == y
                    && zs[index] == z) {
                states[index] = DELETED;
                size--;
                return;
            }

            index = index + 1 & mask;
        }
    }

    void clear() {
        Arrays.fill(states, EMPTY);
        size = 0;
        used = 0;
    }

    int size() {
        return size;
    }

    private void insert(
            int x,
            int y,
            int z,
            int value) {

        int mask = states.length - 1;
        int index = hash(x, y, z) & mask;
        int deleted = -1;

        while (true) {
            byte state = states[index];

            if (state == EMPTY) {
                int target = deleted >= 0 ? deleted : index;

                xs[target] = x;
                ys[target] = y;
                zs[target] = z;
                values[target] = value;
                states[target] = OCCUPIED;
                size++;

                if (deleted < 0) {
                    used++;
                }

                return;
            }

            if (state == DELETED) {
                if (deleted < 0) {
                    deleted = index;
                }
            } else if (xs[index] == x
                    && ys[index] == y
                    && zs[index] == z) {
                values[index] = value;
                return;
            }

            index = index + 1 & mask;
        }
    }

    private void ensureInsertCapacity() {
        if (used < threshold) {
            return;
        }

        if (size * 2 < threshold) {
            rehash(states.length);
            return;
        }

        rehash(states.length << 1);
    }

    private void rehash(
            int capacity) {

        int[] oldXs = xs;
        int[] oldYs = ys;
        int[] oldZs = zs;
        int[] oldValues = values;
        byte[] oldStates = states;

        xs = new int[capacity];
        ys = new int[capacity];
        zs = new int[capacity];
        values = new int[capacity];
        states = new byte[capacity];
        size = 0;
        used = 0;
        threshold = threshold(capacity);

        for (int i = 0; i < oldStates.length; i++) {
            if (oldStates[i] == OCCUPIED) {
                insert(
                        oldXs[i],
                        oldYs[i],
                        oldZs[i],
                        oldValues[i]);
            }
        }
    }

    private static int threshold(
            int capacity) {

        return capacity
                * LOAD_NUMERATOR
                / LOAD_DENOMINATOR;
    }

    private static int hash(
            int x,
            int y,
            int z) {

        int hash = x * 0x9E3779B9;
        hash = Integer.rotateLeft(hash, 11)
                ^ y * 0x85EBCA6B;
        hash = Integer.rotateLeft(hash, 13)
                ^ z * 0xC2B2AE35;
        hash ^= hash >>> 16;
        hash *= 0x7FEB352D;
        hash ^= hash >>> 15;
        hash *= 0x846CA68B;
        return hash ^ hash >>> 16;
    }
}
