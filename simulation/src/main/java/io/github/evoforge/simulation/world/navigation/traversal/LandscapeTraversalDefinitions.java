package io.github.evoforge.simulation.world.navigation.traversal;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

import java.util.Arrays;
import io.github.evoforge.simulation.world.navigation.traversal.SurfaceTraversalCost;

public final class LandscapeTraversalDefinitions {

    private static final int DEFAULT_CAPACITY = 16;

    private SurfaceTraversalCost[] costs =
            new SurfaceTraversalCost[DEFAULT_CAPACITY];
    private long minimumCostUnits = Long.MAX_VALUE;
    private boolean frozen;

    public void put(
            LandscapeDefinitionId id,
            SurfaceTraversalCost cost) {

        if (frozen) {
            throw new IllegalStateException(
                    "landscape traversal definitions are frozen");
        }
        if (id == null) {
            throw new IllegalArgumentException(
                    "id must not be null");
        }
        if (cost == null) {
            throw new IllegalArgumentException(
                    "cost must not be null");
        }

        int index = id.asInt();
        ensureCapacity(index + 1);

        if (costs[index] != null) {
            throw new IllegalStateException(
                    "landscape traversal definition already exists: " + id);
        }

        costs[index] = cost;
        minimumCostUnits = Math.min(
                minimumCostUnits,
                cost.units());
    }

    public boolean has(
            LandscapeDefinitionId id) {

        if (id == null) {
            return false;
        }

        int index = id.asInt();

        return index < costs.length
                && costs[index] != null;
    }

    public SurfaceTraversalCost cost(
            LandscapeDefinitionId id) {

        if (!has(id)) {
            throw new IllegalArgumentException(
                    "landscape traversal definition not found: " + id);
        }

        return costs[id.asInt()];
    }

    public boolean hasAny() {
        return minimumCostUnits != Long.MAX_VALUE;
    }

    public long minimumCostUnits() {
        if (!hasAny()) {
            throw new IllegalStateException(
                    "no landscape traversal definitions exist");
        }
        return minimumCostUnits;
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    private void ensureCapacity(
            int requiredCapacity) {

        if (requiredCapacity <= costs.length) {
            return;
        }

        int newCapacity = Math.max(
                requiredCapacity,
                costs.length * 2);

        costs = Arrays.copyOf(
                costs,
                newCapacity);
    }
}
