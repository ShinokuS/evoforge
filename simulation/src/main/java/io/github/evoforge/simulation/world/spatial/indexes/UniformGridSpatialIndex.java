package io.github.evoforge.simulation.world.spatial.indexes;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.SpatialIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UniformGridSpatialIndex implements SpatialIndex {

    public interface Lookup {

        double cellSize();

        int cellX(double x);

        int cellY(double y);

        int objectCount(
                int cellX,
                int cellY);

        ObjectId objectAt(
                int cellX,
                int cellY,
                int index);
    }

    private final double cellSize;

    private final Map<Long, List<ObjectId>> cells = new HashMap<>();

    private final Lookup lookup = new LookupView();

    public UniformGridSpatialIndex(
            double cellSize) {

        if (!Double.isFinite(cellSize)
                || cellSize <= 0) {

            throw new IllegalArgumentException(
                    "cellSize must be finite and > 0");
        }

        this.cellSize = cellSize;
    }

    public Lookup lookup() {
        return lookup;
    }

    @Override
    public void add(
            ObjectId id,
            double x,
            double y,
            double z) {

        requireId(id);

        long key = cellKey(x, y);

        List<ObjectId> cell = cells.get(key);

        if (cell == null) {
            cell = new ArrayList<>();
            cells.put(key, cell);
        }

        if (cell.contains(id)) {
            throw new IllegalStateException(
                    "object already exists in spatial cell: "
                            + id);
        }

        cell.add(id);
    }

    @Override
    public void move(
            ObjectId id,
            double oldX,
            double oldY,
            double oldZ,
            double newX,
            double newY,
            double newZ) {

        requireId(id);

        long oldKey = cellKey(
                oldX,
                oldY);

        long newKey = cellKey(
                newX,
                newY);

        List<ObjectId> oldCell = cells.get(oldKey);

        if (oldCell == null) {
            throw new IllegalStateException(
                    "object is missing from old spatial cell: "
                            + id);
        }

        int oldIndex = oldCell.indexOf(id);

        if (oldIndex < 0) {
            throw new IllegalStateException(
                    "object is missing from old spatial cell: "
                            + id);
        }

        if (oldKey == newKey) {
            return;
        }

        List<ObjectId> newCell = cells.get(newKey);

        if (newCell != null
                && newCell.contains(id)) {

            throw new IllegalStateException(
                    "object already exists in new spatial cell: "
                            + id);
        }

        oldCell.remove(oldIndex);

        if (oldCell.isEmpty()) {
            cells.remove(oldKey);
        }

        if (newCell == null) {
            newCell = new ArrayList<>();
            cells.put(
                    newKey,
                    newCell);
        }

        newCell.add(id);
    }

    @Override
    public void remove(
            ObjectId id,
            double x,
            double y,
            double z) {

        requireId(id);

        long key = cellKey(x, y);

        List<ObjectId> cell = cells.get(key);

        if (cell == null) {
            throw new IllegalStateException(
                    "object is missing from spatial cell: "
                            + id);
        }

        int index = cell.indexOf(id);

        if (index < 0) {
            throw new IllegalStateException(
                    "object is missing from spatial cell: "
                            + id);
        }

        cell.remove(index);

        if (cell.isEmpty()) {
            cells.remove(key);
        }
    }

    int occupiedCellCount() {
        return cells.size();
    }

    private int cellCoordinate(
            double coordinate) {

        if (!Double.isFinite(coordinate)) {
            throw new IllegalArgumentException(
                    "coordinate must be finite");
        }

        double cell = Math.floor(
                coordinate / cellSize);

        if (cell < Integer.MIN_VALUE
                || cell > Integer.MAX_VALUE) {

            throw new IllegalArgumentException(
                    "coordinate is outside supported grid range");
        }

        return (int) cell;
    }

    private long cellKey(
            double x,
            double y) {

        return cellKey(
                cellCoordinate(x),
                cellCoordinate(y));
    }

    private long cellKey(
            int cellX,
            int cellY) {

        return ((long) cellX << 32)
                | ((long) cellY
                        & 0xFFFF_FFFFL);
    }

    private int objectCount(
            int cellX,
            int cellY) {

        List<ObjectId> cell = cells.get(
                cellKey(
                        cellX,
                        cellY));

        if (cell == null) {
            return 0;
        }

        return cell.size();
    }

    private ObjectId objectAt(
            int cellX,
            int cellY,
            int index) {

        List<ObjectId> cell = cells.get(
                cellKey(
                        cellX,
                        cellY));

        if (cell == null) {
            throw new IndexOutOfBoundsException(
                    "cell is empty");
        }

        return cell.get(index);
    }

    private static void requireId(
            ObjectId id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "id must not be null");
        }
    }

    private final class LookupView
            implements Lookup {

        @Override
        public double cellSize() {
            return cellSize;
        }

        @Override
        public int cellX(double x) {
            return cellCoordinate(x);
        }

        @Override
        public int cellY(double y) {
            return cellCoordinate(y);
        }

        @Override
        public int objectCount(
                int cellX,
                int cellY) {

            return UniformGridSpatialIndex.this
                    .objectCount(
                            cellX,
                            cellY);
        }

        @Override
        public ObjectId objectAt(
                int cellX,
                int cellY,
                int index) {

            return UniformGridSpatialIndex.this
                    .objectAt(
                            cellX,
                            cellY,
                            index);
        }
    }
}