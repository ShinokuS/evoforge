package io.github.evoforge.simulation.world.spatial.indexes;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.ObjectSpatialIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CellSpatialIndex
        implements ObjectSpatialIndex {

    public interface Lookup {

        int objectCount(
                int x,
                int y,
                int z);

        ObjectId objectAt(
                int x,
                int y,
                int z,
                int index);
    }

    private final Map<CellKey, List<ObjectId>> cells = new HashMap<>();

    private final Lookup lookup = new LookupView();

    public Lookup lookup() {
        return lookup;
    }

    @Override
    public void add(
            ObjectId id,
            int x,
            int y,
            int z) {

        requireId(id);

        CellKey key = new CellKey(
                x,
                y,
                z);

        List<ObjectId> cell = cells.get(key);

        if (cell != null
                && cell.contains(id)) {

            throw new IllegalStateException(
                    "object already exists in cell: "
                            + id);
        }

        if (cell == null) {
            cell = new ArrayList<>();

            cells.put(
                    key,
                    cell);
        }

        cell.add(id);
    }

    @Override
    public void move(
            ObjectId id,
            int oldX,
            int oldY,
            int oldZ,
            int newX,
            int newY,
            int newZ) {

        requireId(id);

        CellKey oldKey = new CellKey(
                oldX,
                oldY,
                oldZ);

        CellKey newKey = new CellKey(
                newX,
                newY,
                newZ);

        List<ObjectId> oldCell = cells.get(oldKey);

        if (oldCell == null) {
            throw new IllegalStateException(
                    "source cell does not contain object: "
                            + id);
        }

        int oldIndex = oldCell.indexOf(id);

        if (oldIndex < 0) {
            throw new IllegalStateException(
                    "source cell does not contain object: "
                            + id);
        }

        if (oldKey.equals(newKey)) {
            return;
        }

        List<ObjectId> newCell = cells.get(newKey);

        if (newCell != null
                && newCell.contains(id)) {

            throw new IllegalStateException(
                    "target cell already contains object: "
                            + id);
        }

        if (newCell == null) {
            newCell = new ArrayList<>();
        }

        oldCell.remove(oldIndex);

        if (oldCell.isEmpty()) {
            cells.remove(oldKey);
        }

        if (!cells.containsKey(newKey)) {
            cells.put(
                    newKey,
                    newCell);
        }

        newCell.add(id);
    }

    @Override
    public void remove(
            ObjectId id,
            int x,
            int y,
            int z) {

        requireId(id);

        CellKey key = new CellKey(
                x,
                y,
                z);

        List<ObjectId> cell = cells.get(key);

        if (cell == null) {
            throw new IllegalStateException(
                    "cell does not contain object: "
                            + id);
        }

        int index = cell.indexOf(id);

        if (index < 0) {
            throw new IllegalStateException(
                    "cell does not contain object: "
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

    private int objectCount(
            int x,
            int y,
            int z) {

        List<ObjectId> cell = cells.get(
                new CellKey(
                        x,
                        y,
                        z));

        return cell == null
                ? 0
                : cell.size();
    }

    private ObjectId objectAt(
            int x,
            int y,
            int z,
            int index) {

        List<ObjectId> cell = cells.get(
                new CellKey(
                        x,
                        y,
                        z));

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

    private record CellKey(
            int x,
            int y,
            int z) {
    }

    private final class LookupView
            implements Lookup {

        @Override
        public int objectCount(
                int x,
                int y,
                int z) {

            return CellSpatialIndex.this.objectCount(
                    x,
                    y,
                    z);
        }

        @Override
        public ObjectId objectAt(
                int x,
                int y,
                int z,
                int index) {

            return CellSpatialIndex.this.objectAt(
                    x,
                    y,
                    z,
                    index);
        }
    }
}