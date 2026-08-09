package io.github.evoforge.simulation.world.spatial;

import io.github.evoforge.simulation.world.object.ObjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatialSystemTest {

        @Test
        void placesObject() {
                RecordingIndex index = new RecordingIndex();

                SpatialSystem spatial = new SpatialSystem(index);

                ObjectId id = ObjectId.of(0, 0);

                spatial.place(
                                id,
                                10,
                                20,
                                3);

                TransformLookup transforms = spatial.transforms();

                assertTrue(
                                transforms.has(id));

                assertEquals(
                                10,
                                transforms.x(id));

                assertEquals(
                                20,
                                transforms.y(id));

                assertEquals(
                                3,
                                transforms.z(id));

                assertEquals(
                                1,
                                index.addCount);

                assertEquals(
                                id,
                                index.lastId);

                assertEquals(
                                10,
                                index.x);

                assertEquals(
                                20,
                                index.y);

                assertEquals(
                                3,
                                index.z);
        }

        @Test
        void movesObject() {
                RecordingIndex index = new RecordingIndex();

                SpatialSystem spatial = new SpatialSystem(index);

                ObjectId id = ObjectId.of(0, 0);

                spatial.place(
                                id,
                                10,
                                20,
                                3);

                spatial.move(
                                id,
                                30,
                                40,
                                5);

                TransformLookup transforms = spatial.transforms();

                assertEquals(
                                30,
                                transforms.x(id));

                assertEquals(
                                40,
                                transforms.y(id));

                assertEquals(
                                5,
                                transforms.z(id));

                assertEquals(
                                1,
                                index.moveCount);

                assertEquals(
                                10,
                                index.oldX);

                assertEquals(
                                20,
                                index.oldY);

                assertEquals(
                                3,
                                index.oldZ);

                assertEquals(
                                30,
                                index.newX);

                assertEquals(
                                40,
                                index.newY);

                assertEquals(
                                5,
                                index.newZ);
        }

        @Test
        void removesObject() {
                RecordingIndex index = new RecordingIndex();

                SpatialSystem spatial = new SpatialSystem(index);

                ObjectId id = ObjectId.of(0, 0);

                spatial.place(
                                id,
                                10,
                                20,
                                3);

                spatial.remove(id);

                assertFalse(
                                spatial.transforms().has(id));

                assertEquals(
                                1,
                                index.removeCount);

                assertEquals(
                                id,
                                index.lastId);

                assertEquals(
                                10,
                                index.x);

                assertEquals(
                                20,
                                index.y);

                assertEquals(
                                3,
                                index.z);
        }

        @Test
        void updatesAllIndexes() {
                RecordingIndex first = new RecordingIndex();

                RecordingIndex second = new RecordingIndex();

                SpatialSystem spatial = new SpatialSystem(
                                first,
                                second);

                ObjectId id = ObjectId.of(0, 0);

                spatial.place(
                                id,
                                1,
                                2,
                                3);

                spatial.move(
                                id,
                                4,
                                5,
                                6);

                spatial.remove(id);

                assertEquals(
                                1,
                                first.addCount);

                assertEquals(
                                1,
                                first.moveCount);

                assertEquals(
                                1,
                                first.removeCount);

                assertEquals(
                                1,
                                second.addCount);

                assertEquals(
                                1,
                                second.moveCount);

                assertEquals(
                                1,
                                second.removeCount);
        }

        @Test
        void worksWithoutIndexes() {
                SpatialSystem spatial = new SpatialSystem();

                ObjectId id = ObjectId.of(0, 0);

                spatial.place(
                                id,
                                1,
                                2,
                                3);

                assertTrue(
                                spatial.transforms().has(id));

                spatial.move(
                                id,
                                4,
                                5,
                                6);

                assertEquals(
                                4,
                                spatial.transforms().x(id));

                spatial.remove(id);

                assertFalse(
                                spatial.transforms().has(id));
        }

        @Test
        void rejectsNullIndexesArray() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new SpatialSystem(
                                                (SpatialIndex[]) null));
        }

        @Test
        void rejectsNullIndex() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new SpatialSystem(
                                                new RecordingIndex(),
                                                null));
        }

        @Test
        void rejectsMoveForObjectWithoutTransform() {
                SpatialSystem spatial = new SpatialSystem();

                assertThrows(
                                IllegalStateException.class,
                                () -> spatial.move(
                                                ObjectId.of(0, 0),
                                                1,
                                                2,
                                                3));
        }

        @Test
        void rejectsRemoveForObjectWithoutTransform() {
                SpatialSystem spatial = new SpatialSystem();

                assertThrows(
                                IllegalStateException.class,
                                () -> spatial.remove(
                                                ObjectId.of(0, 0)));
        }

        private static final class RecordingIndex
                        implements SpatialIndex {

                private int addCount;
                private int moveCount;
                private int removeCount;

                private ObjectId lastId;

                private double x;
                private double y;
                private double z;

                private double oldX;
                private double oldY;
                private double oldZ;

                private double newX;
                private double newY;
                private double newZ;

                @Override
                public void add(
                                ObjectId id,
                                double x,
                                double y,
                                double z) {

                        addCount++;

                        lastId = id;

                        this.x = x;
                        this.y = y;
                        this.z = z;
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

                        moveCount++;

                        lastId = id;

                        this.oldX = oldX;
                        this.oldY = oldY;
                        this.oldZ = oldZ;

                        this.newX = newX;
                        this.newY = newY;
                        this.newZ = newZ;
                }

                @Override
                public void remove(
                                ObjectId id,
                                double x,
                                double y,
                                double z) {

                        removeCount++;

                        lastId = id;

                        this.x = x;
                        this.y = y;
                        this.z = z;
                }
        }
}