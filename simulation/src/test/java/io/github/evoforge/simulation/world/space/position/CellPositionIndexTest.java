package io.github.evoforge.simulation.world.space.position;

import io.github.evoforge.simulation.world.object.ObjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CellPositionIndexTest {

        @Test
        void addsAndReadsObject() {
                CellPositionIndex index = new CellPositionIndex();

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
                CellPositionIndex index = new CellPositionIndex();

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
                CellPositionIndex index = new CellPositionIndex();

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
                CellPositionIndex index = new CellPositionIndex();

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
                CellPositionIndex index = new CellPositionIndex();

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
                CellPositionIndex index = new CellPositionIndex();

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
                CellPositionIndex index = new CellPositionIndex();

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
                CellPositionIndex index = new CellPositionIndex();

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
                CellPositionIndex index = new CellPositionIndex();

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
                CellPositionIndex index = new CellPositionIndex();

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
                CellPositionIndex index = new CellPositionIndex();

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