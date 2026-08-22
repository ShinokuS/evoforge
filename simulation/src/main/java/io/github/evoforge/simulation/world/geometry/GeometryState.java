package io.github.evoforge.simulation.world.geometry;

import java.util.HashMap;
import java.util.Map;

final class GeometryState {

    private final Map<Cell, Shape> shapes =
            new HashMap<>();
    private final CellProbe lookupProbe = new CellProbe();

    Shape find(
            int x,
            int y,
            int z) {

        lookupProbe.set(x, y, z);
        return shapes.get(lookupProbe);
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

        lookupProbe.set(x, y, z);
        shapes.remove(lookupProbe);
    }

    private static int hash(
            int x,
            int y,
            int z) {

        int result = Integer.hashCode(x);
        result = 31 * result + Integer.hashCode(y);
        result = 31 * result + Integer.hashCode(z);
        return result;
    }

    private record Cell(
            int x,
            int y,
            int z) {

        @Override
        public int hashCode() {
            return hash(x, y, z);
        }
    }

    private static final class CellProbe {
        private int x;
        private int y;
        private int z;

        private void set(
                int x,
                int y,
                int z) {

            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int hashCode() {
            return hash(x, y, z);
        }

        @Override
        public boolean equals(
                Object other) {

            if (other instanceof Cell cell) {
                return x == cell.x()
                        && y == cell.y()
                        && z == cell.z();
            }
            if (other instanceof CellProbe probe) {
                return x == probe.x
                        && y == probe.y
                        && z == probe.z;
            }
            return false;
        }
    }
}
