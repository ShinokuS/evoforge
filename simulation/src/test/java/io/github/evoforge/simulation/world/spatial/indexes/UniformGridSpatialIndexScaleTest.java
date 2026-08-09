package io.github.evoforge.simulation.world.spatial.indexes;

import io.github.evoforge.simulation.world.object.ObjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UniformGridSpatialIndexScaleTest {

    private static final int OBJECT_COUNT = 100_000;

    private static final int CELL_COUNT = 10_000;

    @Test
    void handlesLargeNumberOfObjects() {
        UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

        UniformGridSpatialIndex.Lookup lookup = index.lookup();

        for (int i = 0; i < OBJECT_COUNT; i++) {

            int cell = i % CELL_COUNT;

            index.add(
                    ObjectId.of(i, 0),
                    cell * 10.0 + 1,
                    1,
                    0);
        }

        assertEquals(
                CELL_COUNT,
                index.occupiedCellCount());

        int expectedPerCell = OBJECT_COUNT / CELL_COUNT;

        int targetCell = 5_432;

        assertEquals(
                expectedPerCell,
                lookup.objectCount(
                        targetCell,
                        0));

        for (int i = 0; i < expectedPerCell; i++) {

            ObjectId expected = ObjectId.of(
                    targetCell
                            + i * CELL_COUNT,
                    0);

            assertEquals(
                    expected,
                    lookup.objectAt(
                            targetCell,
                            0,
                            i));
        }
    }

    @Test
    void handlesMovesAtScale() {
        UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

        UniformGridSpatialIndex.Lookup lookup = index.lookup();

        for (int i = 0; i < OBJECT_COUNT; i++) {

            int cell = i % CELL_COUNT;

            index.add(
                    ObjectId.of(i, 0),
                    cell * 10.0 + 1,
                    1,
                    0);
        }

        ObjectId id = ObjectId.of(50_000, 0);

        index.move(
                id,
                1,
                1,
                0,
                200_001,
                1,
                0);

        assertEquals(
                9,
                lookup.objectCount(
                        0,
                        0));

        assertEquals(
                1,
                lookup.objectCount(
                        20_000,
                        0));

        assertEquals(
                id,
                lookup.objectAt(
                        20_000,
                        0,
                        0));
    }

    @Test
    void handlesRemovalsAtScale() {
        UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

        UniformGridSpatialIndex.Lookup lookup = index.lookup();

        for (int i = 0; i < OBJECT_COUNT; i++) {

            int cell = i % CELL_COUNT;

            index.add(
                    ObjectId.of(i, 0),
                    cell * 10.0 + 1,
                    1,
                    0);
        }

        int targetCell = 1_234;

        for (int i = 0; i < OBJECT_COUNT / CELL_COUNT; i++) {

            int slot = targetCell
                    + i * CELL_COUNT;

            index.remove(
                    ObjectId.of(slot, 0),
                    targetCell * 10.0 + 1,
                    1,
                    0);
        }

        assertEquals(
                0,
                lookup.objectCount(
                        targetCell,
                        0));

        assertEquals(
                CELL_COUNT - 1,
                index.occupiedCellCount());
    }
}