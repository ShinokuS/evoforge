package io.github.evoforge.simulation.world.object;

import java.util.function.Function;

public final class ObjectRepository
        implements ObjectLookup {

    private static final int DEFAULT_CAPACITY = 16;

    private WorldObject[] objects;
    private int[] generations;
    private int[] freeSlots;

    private int freeCount;
    private int nextSlot;
    private int size;

    public ObjectRepository() {
        this(DEFAULT_CAPACITY);
    }

    public ObjectRepository(int initialCapacity) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("initialCapacity must be > 0");
        }

        objects = new WorldObject[initialCapacity];
        generations = new int[initialCapacity];
        freeSlots = new int[initialCapacity];
    }

    public <T extends WorldObject> T create(Function<ObjectId, T> factory) {
        if (factory == null) {
            throw new IllegalArgumentException("factory must not be null");
        }

        int slot = acquireSlot();
        ObjectId id = ObjectId.of(slot, generations[slot]);

        T object;

        try {
            object = factory.apply(id);
        } catch (RuntimeException | Error exception) {
            releaseUnusedSlot(slot);
            throw exception;
        }

        if (object == null) {
            releaseUnusedSlot(slot);
            throw new IllegalArgumentException("factory must not return null");
        }

        if (!id.equals(object.id())) {
            releaseUnusedSlot(slot);
            throw new IllegalArgumentException(
                    "created object must use the supplied ObjectId");
        }

        objects[slot] = object;
        size++;

        return object;
    }

    @Override
    public WorldObject get(ObjectId id) {
        if (!isAlive(id)) {
            return null;
        }

        return objects[id.slot()];
    }

    @Override
    public boolean isAlive(ObjectId id) {
        if (id == null) {
            return false;
        }

        int slot = id.slot();

        if (slot >= nextSlot) {
            return false;
        }

        return objects[slot] != null
                && generations[slot] == id.generation();
    }

    public boolean remove(ObjectId id) {
        if (!isAlive(id)) {
            return false;
        }

        int slot = id.slot();

        objects[slot] = null;
        size--;

        if (generations[slot] < Integer.MAX_VALUE) {
            generations[slot]++;
            freeSlots[freeCount++] = slot;
        }

        return true;
    }

    @Override
    public int size() {
        return size;
    }

    private int acquireSlot() {
        if (freeCount > 0) {
            return freeSlots[--freeCount];
        }

        ensureCapacity(nextSlot + 1);

        return nextSlot++;
    }

    private void releaseUnusedSlot(int slot) {
        freeSlots[freeCount++] = slot;
    }

    private void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity <= objects.length) {
            return;
        }

        int newCapacity = Math.max(
                requiredCapacity,
                objects.length * 2);

        WorldObject[] newObjects = new WorldObject[newCapacity];
        int[] newGenerations = new int[newCapacity];
        int[] newFreeSlots = new int[newCapacity];

        System.arraycopy(objects, 0, newObjects, 0, objects.length);
        System.arraycopy(generations, 0, newGenerations, 0, generations.length);
        System.arraycopy(freeSlots, 0, newFreeSlots, 0, freeCount);

        objects = newObjects;
        generations = newGenerations;
        freeSlots = newFreeSlots;
    }
}