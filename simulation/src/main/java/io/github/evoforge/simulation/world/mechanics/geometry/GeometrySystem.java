package io.github.evoforge.simulation.world.mechanics.geometry;

import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;

public final class GeometrySystem {

    private final TerrainLookup terrain;
    private final GeometryState state =
            new GeometryState();
    private final GeometryLookup lookup;

    public GeometrySystem(
            TerrainLookup terrain) {

        if (terrain == null) {
            throw new IllegalArgumentException(
                    "terrain must not be null");
        }

        this.terrain = terrain;
        lookup = this::find;
    }

    public GeometryLookup lookup() {
        return lookup;
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

        if (!terrain.contains(x, y, z)) {
            throw new IllegalStateException(
                    "terrain does not exist at "
                            + position(x, y, z));
        }

        if (shape == FullShape.INSTANCE) {
            clearShapeOverride(
                    x,
                    y,
                    z);
            return;
        }

        state.put(
                x,
                y,
                z,
                shape);
    }

    public void clearShapeOverride(
            int x,
            int y,
            int z) {
        state.remove(
                x,
                y,
                z);
    }

    private Shape find(
            int x,
            int y,
            int z) {

        if (!terrain.contains(x, y, z)) {
            return null;
        }

        Shape shape =
                state.find(
                        x,
                        y,
                        z);

        if (shape == null) {
            return FullShape.INSTANCE;
        }

        return shape;
    }

    private static String position(
            int x,
            int y,
            int z) {

        return "("
                + x
                + ", "
                + y
                + ", "
                + z
                + ")";
    }
}
