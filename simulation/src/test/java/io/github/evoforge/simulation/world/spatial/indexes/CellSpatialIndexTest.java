package io.github.evoforge.simulation.world.spatial.indexes;

import io.github.evoforge.simulation.world.object.ObjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CellSpatialIndexTest {

        @Test
        void addsAndReadsObject() {
                CellSpatialIndex index = new CellSpatialIndex();

                ObjectId id = ObjectId.of(0, 0);

                index.add(
                                id,
                                10,
                                20,
                                3);

                assertEquals(
                                1,
                                index.lookup().objectCount(
                                                10,
                                                20,
                                                3));

                assertEquals(
                                id,
                                index.lookup().objectAt(
                                                10,
                                                20,
                                                3,
                                                0));
        }

        @Test
        void keepsStableOrderInsideCell() {
                CellSpatialIndex index = new CellSpatialIndex();

                ObjectId first = ObjectId.of(0, 0);

                ObjectId second = ObjectId.of(1, 0);

                index.add(
                                first,
                                10,
                                20,
                                3);

                index.add(
                                second,
                                10,
                                20,
                                3);

                assertEquals(
                                first,
                                index.lookup().objectAt(
                                                10,
                                                20,
                                                3,
                                                0));

                assertEquals(
                                second,
                                index.lookup().objectAt(
                                                10,
                                                20,
                                                3,
                                                1));
        }

        @Test
        void distinguishesDifferentZLevels() {
                CellSpatialIndex index = new CellSpatialIndex();

                ObjectId lower = ObjectId.of(0, 0);

                ObjectId upper = ObjectId.of(1, 0);

                index.add(
                                lower,
                                10,
                                20,
                                0);

                index.add(
                                upper,
                                10,
                                20,
                                1);

                assertEquals(
                                lower,
                                index.lookup().objectAt(
                                                10,
                                                20,
                                                0,
                                                0));

                assertEquals(
                                upper,
                                index.lookup().objectAt(
                                                10,
                                                20,
                                                1,
                                                0));

                assertEquals(
                                2,
                                index.occupiedCellCount());
        }

        @Test
        void supportsNegativeCoordinates() {
                CellSpatialIndex index = new CellSpatialIndex();

                ObjectId id = ObjectId.of(0, 0);

                index.add(
                                id,
                                -100,
                                -200,
                                -30);

                assertEquals(
                                id,
                                index.lookup().objectAt(
                                                -100,
                                                -200,
                                                -30,
                                                0));
        }

        @Test
        void supportsIntegerCoordinateRange() {
                CellSpatialIndex index = new CellSpatialIndex();

                ObjectId first = ObjectId.of(0, 0);

                ObjectId second = ObjectId.of(1, 0);

                index.add(
                                first,
                                Integer.MIN_VALUE,
                                Integer.MIN_VALUE,
                                Integer.MIN_VALUE);

                index.add(
                                second,
                                Integer.MAX_VALUE,
                                Integer.MAX_VALUE,
                                Integer.MAX_VALUE);

                assertEquals(
                                first,
                                index.lookup().objectAt(
                                                Integer.MIN_VALUE,
                                                Integer.MIN_VALUE,
                                                Integer.MIN_VALUE,
                                                0));

                assertEquals(
                                second,
                                index.lookup().objectAt(
                                                Integer.MAX_VALUE,
                                                Integer.MAX_VALUE,
                                                Integer.MAX_VALUE,
                                                0));
        }

        @Test
        void movesBetweenCells() {
                CellSpatialIndex index = new CellSpatialIndex();

                ObjectId id = ObjectId.of(0, 0);

                index.add(
                                id,
                                10,
                                20,
                                0);

                index.move(
                                id,
                                10,
                                20,
                                0,
                                11,
                                20,
                                1);

                assertEquals(
                                0,
                                index.lookup().objectCount(
                                                10,
                                                20,
                                                0));

                assertEquals(
                                id,
                                index.lookup().objectAt(
                                                11,
                                                20,
                                                1,
                                                0));
        }

        @Test
        void movingInsideSameCellChangesNothing() {
                CellSpatialIndex index = new CellSpatialIndex();

                ObjectId id = ObjectId.of(0, 0);

                index.add(
                                id,
                                10,
                                20,
                                0);

                index.move(
                                id,
                                10,
                                20,
                                0,
                                10,
                                20,
                                0);

                assertEquals(
                                1,
                                index.lookup().objectCount(
                                                10,
                                                20,
                                                0));

                assertEquals(
                                1,
                                index.occupiedCellCount());
        }

        @Test
        void removesObjectAndEmptyCell() {
                CellSpatialIndex index = new CellSpatialIndex();

                ObjectId id = ObjectId.of(0, 0);

                index.add(
                                id,
                                10,
                                20,
                                0);

                index.remove(
                                id,
                                10,
                                20,
                                0);

                assertEquals(
                                0,
                                index.lookup().objectCount(
                                                10,
                                                20,
                                                0));

                assertEquals(
                                0,
                                index.occupiedCellCount());
        }

        @Test
        void failedMoveLeavesIndexUnchanged() {
                CellSpatialIndex index = new CellSpatialIndex();

                ObjectId id = ObjectId.of(0, 0);

                index.add(
                                id,
                                10,
                                20,
                                0);

                assertThrows(
                                IllegalStateException.class,
                                () -> index.move(
                                                id,
                                                50,
                                                50,
                                                0,
                                                11,
                                                20,
                                                0));

                assertEquals(
                                id,
                                index.lookup().objectAt(
                                                10,
                                                20,
                                                0,
                                                0));

                assertEquals(
                                0,
                                index.lookup().objectCount(
                                                11,
                                                20,
                                                0));
        }

        @Test
        void failedRemoveLeavesIndexUnchanged() {
                CellSpatialIndex index = new CellSpatialIndex();

                ObjectId id = ObjectId.of(0, 0);

                index.add(
                                id,
                                10,
                                20,
                                0);

                assertThrows(
                                IllegalStateException.class,
                                () -> index.remove(
                                                id,
                                                50,
                                                50,
                                                0));

                assertEquals(
                                id,
                                index.lookup().objectAt(
                                                10,
                                                20,
                                                0,
                                                0));
        }

        @Test
        void rejectsDuplicateAddWithoutChangingCell() {
                CellSpatialIndex index = new CellSpatialIndex();

                ObjectId id = ObjectId.of(0, 0);

                index.add(
                                id,
                                10,
                                20,
                                0);

                assertThrows(
                                IllegalStateException.class,
                                () -> index.add(
                                                id,
                                                10,
                                                20,
                                                0));

                assertEquals(
                                1,
                                index.lookup().objectCount(
                                                10,
                                                20,
                                                0));
        }
}