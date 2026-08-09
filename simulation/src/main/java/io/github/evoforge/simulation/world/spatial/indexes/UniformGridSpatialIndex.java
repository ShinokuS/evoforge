package io.github.evoforge.simulation.world.spatial.indexes;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.SpatialIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UniformGridSpatialIndex implements SpatialIndex {

    private final double cellSize;

    private final Map<Long, List<ObjectId>> cells = new HashMap<>();

    public UniformGridSpatialIndex(double cellSize) {
        if (!Double.isFinite(cellSize)
                || cellSize <= 0) {

            throw new IllegalArgumentException(
                    "cellSize must be finite and > 0");
        }

        this.cellSize = cellSize;
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

        long oldKey = cellKey(oldX, oldY);

        long newKey = cellKey(newX, newY);

        List<ObjectId> oldCell = cells.get(oldKey);

        if (oldCell == null
                || !oldCell.contains(id)) {

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

        oldCell.remove(id);

        if (oldCell.isEmpty()) {
            cells.remove(oldKey);
        }

        if (newCell == null) {
            newCell = new ArrayList<>();
            cells.put(newKey, newCell);
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

        if (cell == null
                || !cell.remove(id)) {

            throw new IllegalStateException(
                    "object is missing from spatial cell: "
                            + id);
        }

        if (cell.isEmpty()) {
            cells.remove(key);
        }
    }

    double cellSize() {
        return cellSize;
    }

    int cellCount() {
        return cells.size();
    }

    boolean contains(
            ObjectId id,
            double x,
            double y) {

        if (id == null) {
            return false;
        }

        List<ObjectId> cell = cells.get(
                cellKey(x, y));

        return cell != null
                && cell.contains(id);
    }

    private long cellKey(
            double x,
            double y) {

        int cellX = cellCoordinate(x);

        int cellY = cellCoordinate(y);

        return ((long) cellX << 32)
                | ((long) cellY & 0xFFFF_FFFFL);
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

    private static void requireId(
            ObjectId id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "id must not be null");
        }
    }
}