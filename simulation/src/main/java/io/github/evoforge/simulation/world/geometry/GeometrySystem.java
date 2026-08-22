package io.github.evoforge.simulation.world.geometry;

import java.util.TreeMap;

import io.github.evoforge.simulation.world.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.geometry.ShapeTraversalFactor;
import io.github.evoforge.simulation.world.geometry.ShapeTraversalLowerBoundLookup;

public final class GeometrySystem {

    private final TerrainLookup terrain;
    private final GeometryState overrides =
            new GeometryState();
    private final TreeMap<Integer, Integer> overrideMinimumFactors =
            new TreeMap<>();
    private final GeometryLookup lookup;
    private final ShapeTraversalLowerBoundLookup traversalBounds;

    public GeometrySystem(
            TerrainLookup terrain) {

        if (terrain == null) {
            throw new IllegalArgumentException(
                    "terrain must not be null");
        }

        this.terrain = terrain;
        lookup = this::find;
        traversalBounds = this::minimumTraversalFactor;
    }

    public GeometryLookup lookup() {
        return lookup;
    }

    public ShapeTraversalLowerBoundLookup traversalBounds() {
        return traversalBounds;
    }

    public void setShape(
            int x,
            int y,
            int z,
            Shape shape) {

        if (shape == null) {
            throw new IllegalArgumentException(
                    "shape must not be null");
        }
        if (terrain.find(x, y, z) == null) {
            throw new IllegalStateException(
                    "cannot set Shape without terrain");
        }

        Shape previous = overrides.find(x, y, z);

        if (shape == FullShape.INSTANCE) {
            if (previous != null) {
                removeMinimum(previous);
                overrides.remove(x, y, z);
            }
            return;
        }

        int minimum = ShapeTraversalFactor.requirePositive(
                shape.minimumTraversalFactor());

        if (previous != null) {
            removeMinimum(previous);
        }

        overrides.put(x, y, z, shape);
        addMinimum(minimum);
    }

    public void clearShapeOverride(
            int x,
            int y,
            int z) {

        Shape previous = overrides.find(x, y, z);
        if (previous != null) {
            removeMinimum(previous);
            overrides.remove(x, y, z);
        }
    }

    private Shape find(
            int x,
            int y,
            int z) {

        if (terrain.find(x, y, z) == null) {
            return null;
        }

        Shape override = overrides.find(x, y, z);

        return override != null
                ? override
                : FullShape.INSTANCE;
    }

    private int minimumTraversalFactor() {
        if (overrideMinimumFactors.isEmpty()) {
            return ShapeTraversalFactor.NEUTRAL;
        }
        return Math.min(
                ShapeTraversalFactor.NEUTRAL,
                overrideMinimumFactors.firstKey());
    }

    private void addMinimum(
            int minimum) {

        overrideMinimumFactors.merge(
                minimum,
                1,
                Integer::sum);
    }

    private void removeMinimum(
            Shape shape) {

        int minimum = ShapeTraversalFactor.requirePositive(
                shape.minimumTraversalFactor());
        Integer count = overrideMinimumFactors.get(minimum);

        if (count == null) {
            throw new IllegalStateException(
                    "missing Shape traversal lower-bound count");
        }
        if (count == 1) {
            overrideMinimumFactors.remove(minimum);
        } else {
            overrideMinimumFactors.put(
                    minimum,
                    count - 1);
        }
    }
}
