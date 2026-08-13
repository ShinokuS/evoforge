package io.github.evoforge.simulation.world.mechanics.occupancy;

import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

import java.util.Arrays;

/** Immutable-after-bootstrap object-definition facts for exclusive cell occupancy. */
public final class OccupancyDefinitions {

    private static final int DEFAULT_CAPACITY = 16;

    private boolean[] exclusive =
            new boolean[DEFAULT_CAPACITY];
    private boolean[] defined =
            new boolean[DEFAULT_CAPACITY];

    private boolean frozen;

    public void put(
            ObjectDefinitionId id,
            boolean requiresExclusiveCell) {

        if (frozen) {
            throw new IllegalStateException(
                    "occupancy definitions are frozen");
        }
        if (id == null) {
            throw new IllegalArgumentException(
                    "id must not be null");
        }

        int index = id.asInt();
        ensureCapacity(index + 1);

        if (defined[index]) {
            throw new IllegalStateException(
                    "occupancy definition already exists: " + id);
        }

        defined[index] = true;
        exclusive[index] = requiresExclusiveCell;
    }

    /**
     * Returns whether instances of the definition require exclusive ownership
     * of their current discrete cell. Definitions without an occupancy aspect
     * are deliberately transparent to exclusive occupancy.
     */
    public boolean requiresExclusiveCell(
            ObjectDefinitionId id) {

        if (id == null) {
            return false;
        }

        int index = id.asInt();
        return index < defined.length
                && defined[index]
                && exclusive[index];
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    private void ensureCapacity(
            int requiredCapacity) {

        if (requiredCapacity <= exclusive.length) {
            return;
        }

        int newCapacity = Math.max(
                requiredCapacity,
                exclusive.length * 2);

        exclusive = Arrays.copyOf(
                exclusive,
                newCapacity);
        defined = Arrays.copyOf(
                defined,
                newCapacity);
    }
}
