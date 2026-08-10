package io.github.evoforge.simulation.world.spatial;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.indexes.CellSpatialIndex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatialSystemIntegrationTest {

        @Test
        void placeSynchronizesTransformAndCellIndex() {
                CellSpatialIndex index = new CellSpatialIndex();

                CellSpatialIndex.Lookup lookup = index.lookup();

                SpatialSystem spatial = new SpatialSystem(index);

                ObjectId id = ObjectId.of(0, 0);

                spatial.place(
                                id,
                                15,
                                25,
                                3);

                TransformLookup transforms = spatial.transforms();

                assertTrue(
                                transforms.has(id));

                assertEquals(
                                15,
                                transforms.x(id));

                assertEquals(
                                25,
                                transforms.y(id));

                assertEquals(
                                3,
                                transforms.z(id));

                assertEquals(
                                1,
                                lookup.objectCount(
                                                15,
                                                25,
                                                3));

                assertEquals(
                                id,
                                lookup.objectAt(
                                                15,
                                                25,
                                                3,
                                                0));
        }

        @Test
        void moveSynchronizesTransformAndCellIndex() {
                CellSpatialIndex index = new CellSpatialIndex();

                CellSpatialIndex.Lookup lookup = index.lookup();

                SpatialSystem spatial = new SpatialSystem(index);

                ObjectId id = ObjectId.of(0, 0);

                spatial.place(
                                id,
                                15,
                                25,
                                3);

                spatial.move(
                                id,
                                35,
                                45,
                                7);

                TransformLookup transforms = spatial.transforms();

                assertEquals(
                                35,
                                transforms.x(id));

                assertEquals(
                                45,
                                transforms.y(id));

                assertEquals(
                                7,
                                transforms.z(id));

                assertEquals(
                                0,
                                lookup.objectCount(
                                                15,
                                                25,
                                                3));

                assertEquals(
                                1,
                                lookup.objectCount(
                                                35,
                                                45,
                                                7));

                assertEquals(
                                id,
                                lookup.objectAt(
                                                35,
                                                45,
                                                7,
                                                0));
        }

        @Test
        void differentZLevelsAreDifferentCells() {
                CellSpatialIndex index = new CellSpatialIndex();

                CellSpatialIndex.Lookup lookup = index.lookup();

                SpatialSystem spatial = new SpatialSystem(index);

                ObjectId lower = ObjectId.of(0, 0);

                ObjectId upper = ObjectId.of(1, 0);

                spatial.place(
                                lower,
                                10,
                                20,
                                0);

                spatial.place(
                                upper,
                                10,
                                20,
                                1);

                assertEquals(
                                1,
                                lookup.objectCount(
                                                10,
                                                20,
                                                0));

                assertEquals(
                                1,
                                lookup.objectCount(
                                                10,
                                                20,
                                                1));

                assertEquals(
                                lower,
                                lookup.objectAt(
                                                10,
                                                20,
                                                0,
                                                0));

                assertEquals(
                                upper,
                                lookup.objectAt(
                                                10,
                                                20,
                                                1,
                                                0));
        }

        @Test
        void removeSynchronizesTransformAndCellIndex() {
                CellSpatialIndex index = new CellSpatialIndex();

                CellSpatialIndex.Lookup lookup = index.lookup();

                SpatialSystem spatial = new SpatialSystem(index);

                ObjectId id = ObjectId.of(0, 0);

                spatial.place(
                                id,
                                15,
                                25,
                                3);

                spatial.remove(id);

                assertFalse(
                                spatial.transforms().has(id));

                assertEquals(
                                0,
                                lookup.objectCount(
                                                15,
                                                25,
                                                3));
        }

        @Test
        void multipleObjectsRemainSynchronized() {
                CellSpatialIndex index = new CellSpatialIndex();

                CellSpatialIndex.Lookup lookup = index.lookup();

                SpatialSystem spatial = new SpatialSystem(index);

                ObjectId first = ObjectId.of(0, 0);

                ObjectId second = ObjectId.of(1, 0);

                spatial.place(
                                first,
                                10,
                                20,
                                0);

                spatial.place(
                                second,
                                10,
                                20,
                                0);

                assertEquals(
                                2,
                                lookup.objectCount(
                                                10,
                                                20,
                                                0));

                spatial.move(
                                first,
                                30,
                                40,
                                1);

                assertEquals(
                                1,
                                lookup.objectCount(
                                                10,
                                                20,
                                                0));

                assertEquals(
                                second,
                                lookup.objectAt(
                                                10,
                                                20,
                                                0,
                                                0));

                assertEquals(
                                1,
                                lookup.objectCount(
                                                30,
                                                40,
                                                1));

                assertEquals(
                                first,
                                lookup.objectAt(
                                                30,
                                                40,
                                                1,
                                                0));

                spatial.remove(second);

                assertFalse(
                                spatial.transforms().has(second));

                assertEquals(
                                0,
                                lookup.objectCount(
                                                10,
                                                20,
                                                0));

                assertTrue(
                                spatial.transforms().has(first));

                assertEquals(
                                first,
                                lookup.objectAt(
                                                30,
                                                40,
                                                1,
                                                0));
        }
}