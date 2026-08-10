package io.github.evoforge.simulation.world.mechanics.geometry;

import java.util.HashMap;
import java.util.Map;

final class GeometryState {

    private final Map<Cell, Shape> shapes =
            new HashMap<>();

    Shape find(
            int x,
            int y,
            int z) {

        return shapes.get(
                new Cell(x, y, z));
    }

    void put(
            int x,
            int y,
            int z,
            Shape shape) {

        if (shape == null) {
            throw new IllegalArgumentException(
                    "shape must not be null");
        }

        if (shape == FullShape.INSTANCE) {
            throw new IllegalArgumentException(
                    "full shape must not be stored as override");
        }

        shapes.put(
                new Cell(x, y, z),
                shape);
    }

    void remove(
            int x,
            int y,
            int z) {

        shapes.remove(
                new Cell(x, y, z));
    }

    private record Cell(
            int x,
            int y,
            int z) {
    }
}
