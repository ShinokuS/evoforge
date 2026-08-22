package io.github.evoforge.simulation.world.geometry;

import io.github.evoforge.simulation.world.space.WorldBounds;

/**
 * Geometry view that can optionally close a simulation inside explicit finite bounds.
 *
 * <p>Outside a configured world box, space is exposed to geometry consumers as
 * {@link FullShape}. This makes the boundary physically closed without teaching
 * Water, Navigation, Movement or other consumers about map edges. With no configured
 * bounds the delegate retains the previous unbounded-world semantics.</p>
 */
public final class WorldGeometryLookup implements GeometryLookup {

    private final GeometryLookup delegate;
    private WorldBounds bounds;

    public WorldGeometryLookup(
            GeometryLookup delegate) {

        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    public void configureBounds(
            WorldBounds bounds) {

        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        if (this.bounds != null) {
            throw new IllegalStateException("world bounds are already configured");
        }
        this.bounds = bounds;
    }

    public boolean bounded() {
        return bounds != null;
    }

    public boolean contains(
            int x,
            int y,
            int z) {

        return bounds == null || bounds.contains(x, y, z);
    }

    @Override
    public Shape find(
            int x,
            int y,
            int z) {

        if (!contains(x, y, z)) {
            return FullShape.INSTANCE;
        }
        return delegate.find(x, y, z);
    }
}
