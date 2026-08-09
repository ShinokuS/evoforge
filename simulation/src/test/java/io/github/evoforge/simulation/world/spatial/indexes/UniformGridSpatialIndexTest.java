package io.github.evoforge.simulation.world.spatial.indexes;

import io.github.evoforge.simulation.world.object.ObjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UniformGridSpatialIndexTest {

        @Test
        void exposesGridProperties() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                UniformGridSpatialIndex.Lookup lookup = index.lookup();

                assertEquals(
                                10,
                                lookup.cellSize());

                assertEquals(
                                1,
                                lookup.cellX(15));

                assertEquals(
                                2,
                                lookup.cellY(25));
        }

        @Test
        void convertsNegativeCoordinatesUsingFloor() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                UniformGridSpatialIndex.Lookup lookup = index.lookup();

                assertEquals(
                                -1,
                                lookup.cellX(-0.1));

                assertEquals(
                                -1,
                                lookup.cellY(-10));

                assertEquals(
                                -2,
                                lookup.cellX(-10.1));
        }

        @Test
        void separatesCellBoundary() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                UniformGridSpatialIndex.Lookup lookup = index.lookup();

                assertEquals(
                                0,
                                lookup.cellX(9.999));

                assertEquals(
                                1,
                                lookup.cellX(10));
        }

        @Test
        void addsAndReadsObject() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                UniformGridSpatialIndex.Lookup lookup = index.lookup();

                ObjectId id = ObjectId.of(0, 0);

                index.add(
                                id,
                                15,
                                25,
                                0);

                assertEquals(
                                1,
                                lookup.objectCount(
                                                1,
                                                2));

                assertEquals(
                                id,
                                lookup.objectAt(
                                                1,
                                                2,
                                                0));
        }

        @Test
        void storesMultipleObjectsInSameCell() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                UniformGridSpatialIndex.Lookup lookup = index.lookup();

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
                                100);

                assertEquals(
                                2,
                                lookup.objectCount(
                                                1,
                                                2));

                assertEquals(
                                first,
                                lookup.objectAt(
                                                1,
                                                2,
                                                0));

                assertEquals(
                                second,
                                lookup.objectAt(
                                                1,
                                                2,
                                                1));

                assertEquals(
                                1,
                                index.occupiedCellCount());
        }

        @Test
        void storesObjectsInDifferentCells() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                UniformGridSpatialIndex.Lookup lookup = index.lookup();

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
                                1,
                                lookup.objectCount(
                                                0,
                                                0));

                assertEquals(
                                1,
                                lookup.objectCount(
                                                1,
                                                0));

                assertEquals(
                                2,
                                index.occupiedCellCount());
        }

        @Test
        void moveInsideSameCellKeepsEntry() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                UniformGridSpatialIndex.Lookup lookup = index.lookup();

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
                                50);

                assertEquals(
                                1,
                                lookup.objectCount(
                                                1,
                                                2));

                assertEquals(
                                id,
                                lookup.objectAt(
                                                1,
                                                2,
                                                0));

                assertEquals(
                                1,
                                index.occupiedCellCount());
        }

        @Test
        void movesObjectBetweenCells() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                UniformGridSpatialIndex.Lookup lookup = index.lookup();

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

                assertEquals(
                                0,
                                lookup.objectCount(
                                                1,
                                                2));

                assertEquals(
                                1,
                                lookup.objectCount(
                                                3,
                                                4));

                assertEquals(
                                id,
                                lookup.objectAt(
                                                3,
                                                4,
                                                0));

                assertEquals(
                                1,
                                index.occupiedCellCount());
        }

        @Test
        void removesObjectAndEmptyCell() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                UniformGridSpatialIndex.Lookup lookup = index.lookup();

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

                assertEquals(
                                0,
                                lookup.objectCount(
                                                1,
                                                2));

                assertEquals(
                                0,
                                index.occupiedCellCount());
        }

        @Test
        void keepsCellWhileAnotherObjectRemains() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                UniformGridSpatialIndex.Lookup lookup = index.lookup();

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

                assertEquals(
                                1,
                                lookup.objectCount(
                                                1,
                                                2));

                assertEquals(
                                second,
                                lookup.objectAt(
                                                1,
                                                2,
                                                0));

                assertEquals(
                                1,
                                index.occupiedCellCount());
        }

        @Test
        void ignoresZForCellMembership() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                UniformGridSpatialIndex.Lookup lookup = index.lookup();

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
                                2,
                                lookup.objectCount(
                                                1,
                                                2));
        }

        @Test
        void returnsZeroForEmptyCell() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                assertEquals(
                                0,
                                index.lookup().objectCount(
                                                100,
                                                100));
        }

        @Test
        void rejectsObjectAtForEmptyCell() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                assertThrows(
                                IndexOutOfBoundsException.class,
                                () -> index.lookup().objectAt(
                                                100,
                                                100,
                                                0));
        }

        @Test
        void rejectsObjectAtOutsideCellRange() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                ObjectId id = ObjectId.of(0, 0);

                index.add(
                                id,
                                15,
                                25,
                                0);

                assertThrows(
                                IndexOutOfBoundsException.class,
                                () -> index.lookup().objectAt(
                                                1,
                                                2,
                                                1));
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
        void rejectsMoveWhenObjectIsMissing() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                assertThrows(
                                IllegalStateException.class,
                                () -> index.move(
                                                ObjectId.of(0, 0),
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
        void rejectsNullObjectId() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> index.add(
                                                null,
                                                0,
                                                0,
                                                0));
        }

        @Test
        void rejectsNonFiniteCoordinates() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                UniformGridSpatialIndex.Lookup lookup = index.lookup();

                assertThrows(
                                IllegalArgumentException.class,
                                () -> lookup.cellX(
                                                Double.NaN));

                assertThrows(
                                IllegalArgumentException.class,
                                () -> lookup.cellY(
                                                Double.POSITIVE_INFINITY));
        }

        @Test
        void rejectsCoordinateOutsideGridRange() {
                UniformGridSpatialIndex index = new UniformGridSpatialIndex(10);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> index.lookup().cellX(
                                                Double.MAX_VALUE));
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