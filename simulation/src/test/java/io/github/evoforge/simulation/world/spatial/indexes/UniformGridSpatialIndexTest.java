package io.github.evoforge.simulation.world.spatial.indexes;

import io.github.evoforge.simulation.world.object.ObjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniformGridSpatialIndexTest {

        @Test
        void addsObjectToCell() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                ObjectId id = ObjectId.of(0, 0);

                index.add(
                                id,
                                15,
                                25,
                                0);

                assertTrue(
                                index.contains(
                                                id,
                                                15,
                                                25));

                assertEquals(
                                1,
                                index.cellCount());
        }

        @Test
        void storesMultipleObjectsInSameCell() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                ObjectId first = ObjectId.of(0, 0);

                ObjectId second = ObjectId.of(1, 0);

                index.add(
                                first,
                                11,
                                21,
                                0);

                index.add(
                                second,
                                19,
                                29,
                                5);

                assertTrue(
                                index.contains(
                                                first,
                                                15,
                                                25));

                assertTrue(
                                index.contains(
                                                second,
                                                15,
                                                25));

                assertEquals(
                                1,
                                index.cellCount());
        }

        @Test
        void movingInsideSameCellDoesNotMoveIndexEntry() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                ObjectId id = ObjectId.of(0, 0);

                index.add(
                                id,
                                11,
                                21,
                                0);

                index.move(
                                id,
                                11,
                                21,
                                0,
                                19,
                                29,
                                10);

                assertTrue(
                                index.contains(
                                                id,
                                                19,
                                                29));

                assertEquals(
                                1,
                                index.cellCount());
        }

        @Test
        void movesObjectBetweenCells() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                ObjectId id = ObjectId.of(0, 0);

                index.add(
                                id,
                                15,
                                25,
                                0);

                index.move(
                                id,
                                15,
                                25,
                                0,
                                35,
                                45,
                                0);

                assertFalse(
                                index.contains(
                                                id,
                                                15,
                                                25));

                assertTrue(
                                index.contains(
                                                id,
                                                35,
                                                45));

                assertEquals(
                                1,
                                index.cellCount());
        }

        @Test
        void removesObject() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                ObjectId id = ObjectId.of(0, 0);

                index.add(
                                id,
                                15,
                                25,
                                0);

                index.remove(
                                id,
                                15,
                                25,
                                0);

                assertFalse(
                                index.contains(
                                                id,
                                                15,
                                                25));

                assertEquals(
                                0,
                                index.cellCount());
        }

        @Test
        void keepsCellWhileOtherObjectsRemain() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                ObjectId first = ObjectId.of(0, 0);

                ObjectId second = ObjectId.of(1, 0);

                index.add(
                                first,
                                11,
                                21,
                                0);

                index.add(
                                second,
                                12,
                                22,
                                0);

                index.remove(
                                first,
                                11,
                                21,
                                0);

                assertFalse(
                                index.contains(
                                                first,
                                                11,
                                                21));

                assertTrue(
                                index.contains(
                                                second,
                                                12,
                                                22));

                assertEquals(
                                1,
                                index.cellCount());
        }

        @Test
        void handlesNegativeCoordinatesUsingFloor() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                ObjectId id = ObjectId.of(0, 0);

                index.add(
                                id,
                                -0.1,
                                -0.1,
                                0);

                assertTrue(
                                index.contains(
                                                id,
                                                -5,
                                                -5));

                assertFalse(
                                index.contains(
                                                id,
                                                5,
                                                5));
        }

        @Test
        void separatesAdjacentCells() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                ObjectId first = ObjectId.of(0, 0);

                ObjectId second = ObjectId.of(1, 0);

                index.add(
                                first,
                                9.999,
                                5,
                                0);

                index.add(
                                second,
                                10,
                                5,
                                0);

                assertEquals(
                                2,
                                index.cellCount());
        }

        @Test
        void ignoresZForCellMembership() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                ObjectId first = ObjectId.of(0, 0);

                ObjectId second = ObjectId.of(1, 0);

                index.add(
                                first,
                                15,
                                25,
                                0);

                index.add(
                                second,
                                15,
                                25,
                                1000);

                assertEquals(
                                1,
                                index.cellCount());

                assertTrue(
                                index.contains(
                                                first,
                                                15,
                                                25));

                assertTrue(
                                index.contains(
                                                second,
                                                15,
                                                25));
        }

        @Test
        void rejectsDuplicateObjectInCell() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                ObjectId id = ObjectId.of(0, 0);

                index.add(
                                id,
                                15,
                                25,
                                0);

                assertThrows(
                                IllegalStateException.class,
                                () -> index.add(
                                                id,
                                                15,
                                                25,
                                                0));
        }

        @Test
        void rejectsMoveWhenObjectIsMissingFromOldCell() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                ObjectId id = ObjectId.of(0, 0);

                assertThrows(
                                IllegalStateException.class,
                                () -> index.move(
                                                id,
                                                10,
                                                20,
                                                0,
                                                30,
                                                40,
                                                0));
        }

        @Test
        void rejectsRemoveWhenObjectIsMissing() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                assertThrows(
                                IllegalStateException.class,
                                () -> index.remove(
                                                ObjectId.of(0, 0),
                                                10,
                                                20,
                                                0));
        }

        @Test
        void rejectsInvalidCellSize() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new UniformGridSpatialIndex(0));

                assertThrows(
                                IllegalArgumentException.class,
                                () -> new UniformGridSpatialIndex(-1));

                assertThrows(
                                IllegalArgumentException.class,
                                () -> new UniformGridSpatialIndex(
                                                Double.NaN));

                assertThrows(
                                IllegalArgumentException.class,
                                () -> new UniformGridSpatialIndex(
                                                Double.POSITIVE_INFINITY));
        }
}