package io.github.evoforge.simulation.mechanics.movement;

import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

import java.util.Arrays;

public final class MovementDefinitions {

    private static final int DEFAULT_CAPACITY = 16;

    private MovementRate[] rates =
            new MovementRate[DEFAULT_CAPACITY];

    private boolean frozen;

    public void put(
            ObjectDefinitionId id,
            MovementRate rate) {

        if (frozen) {
            throw new IllegalStateException(
                    "movement definitions are frozen");
        }
        if (id == null) {
            throw new IllegalArgumentException(
                    "id must not be null");
        }
        if (rate == null) {
            throw new IllegalArgumentException(
                    "rate must not be null");
        }

        int index = id.asInt();

        ensureCapacity(index + 1);

        if (rates[index] != null) {
            throw new IllegalStateException(
                    "movement definition already exists: " + id);
        }

        rates[index] = rate;
    }

    public boolean has(
            ObjectDefinitionId id) {

        if (id == null) {
            return false;
        }

        int index = id.asInt();

        return index < rates.length
                && rates[index] != null;
    }

    public MovementRate rate(
            ObjectDefinitionId id) {

        if (!has(id)) {
            throw new IllegalArgumentException(
                    "movement definition not found: " + id);
        }

        return rates[id.asInt()];
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    private void ensureCapacity(
            int requiredCapacity) {

        if (requiredCapacity <= rates.length) {
            return;
        }

        int newCapacity = Math.max(
                requiredCapacity,
                rates.length * 2);

        rates = Arrays.copyOf(
                rates,
                newCapacity);
    }
}
