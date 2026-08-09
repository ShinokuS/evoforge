package io.github.evoforge.simulation.world.definition.physical;

import io.github.evoforge.simulation.world.definition.DefinitionId;

public final class PhysicalDefinitions {

    private static final int DEFAULT_CAPACITY = 16;

    private double[] masses = new double[DEFAULT_CAPACITY];

    private boolean[] present = new boolean[DEFAULT_CAPACITY];

    private boolean frozen;

    public void put(
            DefinitionId id,
            double mass) {

        if (frozen) {
            throw new IllegalStateException(
                    "physical definitions are frozen");
        }

        if (id == null) {
            throw new IllegalArgumentException(
                    "id must not be null");
        }

        if (!Double.isFinite(mass) || mass <= 0) {
            throw new IllegalArgumentException(
                    "mass must be finite and > 0");
        }

        int index = id.asInt();

        ensureCapacity(index + 1);

        if (present[index]) {
            throw new IllegalStateException(
                    "physical definition already exists: " + id);
        }

        masses[index] = mass;
        present[index] = true;
    }

    public boolean has(DefinitionId id) {
        if (id == null) {
            return false;
        }

        int index = id.asInt();

        return index < present.length
                && present[index];
    }

    public double mass(DefinitionId id) {
        if (!has(id)) {
            throw new IllegalArgumentException(
                    "physical definition not found: " + id);
        }

        return masses[id.asInt()];
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    private void ensureCapacity(
            int requiredCapacity) {

        if (requiredCapacity <= masses.length) {
            return;
        }

        int newCapacity = Math.max(
                requiredCapacity,
                masses.length * 2);

        double[] newMasses = new double[newCapacity];

        boolean[] newPresent = new boolean[newCapacity];

        System.arraycopy(
                masses,
                0,
                newMasses,
                0,
                masses.length);

        System.arraycopy(
                present,
                0,
                newPresent,
                0,
                present.length);

        masses = newMasses;
        present = newPresent;
    }
}