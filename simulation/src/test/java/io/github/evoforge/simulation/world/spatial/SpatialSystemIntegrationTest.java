package io.github.evoforge.simulation.world.spatial;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.spatial.indexes.UniformGridSpatialIndex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatialSystemIntegrationTest {

        @Test
        void placeSynchronizesTransformAndGrid() {
                UniformGridSpatialIndex grid = new UniformGridSpatialIndex(10);

                UniformGridSpatialIndex.Lookup lookup = grid.lookup();

                SpatialSystem spatial = new SpatialSystem(grid);

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
        void moveSynchronizesTransformAndGrid() {
                UniformGridSpatialIndex grid = new UniformGridSpatialIndex(10);

                UniformGridSpatialIndex.Lookup lookup = grid.lookup();

                SpatialSystem spatial = new SpatialSystem(grid);

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
        }

        @Test
        void moveInsideSameCellKeepsGridMembership() {
                UniformGridSpatialIndex grid = new UniformGridSpatialIndex(10);

                UniformGridSpatialIndex.Lookup lookup = grid.lookup();

                SpatialSystem spatial = new SpatialSystem(grid);

                ObjectId id = ObjectId.of(0, 0);

                spatial.place(
                                id,
                                11,
                                21,
                                0);

                spatial.move(
                                id,
                                19,
                                29,
                                5);

                assertEquals(
                                19,
                                spatial.transforms().x(id));

                assertEquals(
                                29,
                                spatial.transforms().y(id));

                assertEquals(
                                5,
                                spatial.transforms().z(id));

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
        void removeSynchronizesTransformAndGrid() {
                UniformGridSpatialIndex grid = new UniformGridSpatialIndex(10);

                UniformGridSpatialIndex.Lookup lookup = grid.lookup();

                SpatialSystem spatial = new SpatialSystem(grid);

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
                                                1,
                                                2));
        }

        @Test
        void multipleObjectsRemainSynchronized() {
                UniformGridSpatialIndex grid = new UniformGridSpatialIndex(10);

                UniformGridSpatialIndex.Lookup lookup = grid.lookup();

                SpatialSystem spatial = new SpatialSystem(grid);

                ObjectId first = ObjectId.of(0, 0);

                ObjectId second = ObjectId.of(1, 0);

                spatial.place(
                                first,
                                11,
                                21,
                                0);

                spatial.place(
                                second,
                                12,
                                22,
                                0);

                assertEquals(
                                2,
                                lookup.objectCount(
                                                1,
                                                2));

                spatial.move(
                                first,
                                31,
                                41,
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
                                lookup.objectCount(
                                                3,
                                                4));

                assertEquals(
                                first,
                                lookup.objectAt(
                                                3,
                                                4,
                                                0));

                spatial.remove(second);

                assertFalse(
                                spatial.transforms().has(second));

                assertEquals(
                                0,
                                lookup.objectCount(
                                                1,
                                                2));

                assertTrue(
                                spatial.transforms().has(first));

                assertEquals(
                                first,
                                lookup.objectAt(
                                                3,
                                                4,
                                                0));
        }
}