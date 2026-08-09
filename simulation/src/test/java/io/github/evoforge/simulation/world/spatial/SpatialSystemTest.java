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

        @Test
        void failedPlaceRollsBackPreviousChanges() {
                RecordingIndex first = new RecordingIndex();

                FailingIndex second = new FailingIndex();

                second.failAdd = true;

                SpatialSystem spatial = new SpatialSystem(
                                first,
                                second);

                ObjectId id = ObjectId.of(0, 0);

                assertThrows(
                                IllegalStateException.class,
                                () -> spatial.place(
                                                id,
                                                10,
                                                20,
                                                3));

                assertFalse(
                                spatial.transforms().has(id));

                assertEquals(
                                1,
                                first.addCount);

                assertEquals(
                                1,
                                first.removeCount);

                assertTrue(
                                spatial.isFaulted());
        }

        @Test
        void failedMoveRollsBackPreviousChanges() {
                RecordingIndex first = new RecordingIndex();

                FailingIndex second = new FailingIndex();

                SpatialSystem spatial = new SpatialSystem(
                                first,
                                second);

                ObjectId id = ObjectId.of(0, 0);

                spatial.place(
                                id,
                                10,
                                20,
                                3);

                second.failMove = true;

                assertThrows(
                                IllegalStateException.class,
                                () -> spatial.move(
                                                id,
                                                30,
                                                40,
                                                5));

                assertEquals(
                                10,
                                spatial.transforms().x(id));

                assertEquals(
                                20,
                                spatial.transforms().y(id));

                assertEquals(
                                3,
                                spatial.transforms().z(id));

                assertEquals(
                                2,
                                first.moveCount);

                assertEquals(
                                30,
                                first.oldX);

                assertEquals(
                                40,
                                first.oldY);

                assertEquals(
                                5,
                                first.oldZ);

                assertEquals(
                                10,
                                first.newX);

                assertEquals(
                                20,
                                first.newY);

                assertEquals(
                                3,
                                first.newZ);

                assertTrue(
                                spatial.isFaulted());
        }

        @Test
        void failedRemoveRollsBackPreviousChanges() {
                RecordingIndex first = new RecordingIndex();

                FailingIndex second = new FailingIndex();

                SpatialSystem spatial = new SpatialSystem(
                                first,
                                second);

                ObjectId id = ObjectId.of(0, 0);

                spatial.place(
                                id,
                                10,
                                20,
                                3);

                second.failRemove = true;

                assertThrows(
                                IllegalStateException.class,
                                () -> spatial.remove(id));

                assertTrue(
                                spatial.transforms().has(id));

                assertEquals(
                                10,
                                spatial.transforms().x(id));

                assertEquals(
                                20,
                                spatial.transforms().y(id));

                assertEquals(
                                3,
                                spatial.transforms().z(id));

                assertEquals(
                                2,
                                first.addCount);

                assertEquals(
                                1,
                                first.removeCount);

                assertTrue(
                                spatial.isFaulted());
        }

        @Test
        void doesNotUpdateIndexesAfterFailure() {
                RecordingIndex first = new RecordingIndex();

                FailingIndex second = new FailingIndex();

                RecordingIndex third = new RecordingIndex();

                second.failAdd = true;

                SpatialSystem spatial = new SpatialSystem(
                                first,
                                second,
                                third);

                assertThrows(
                                IllegalStateException.class,
                                () -> spatial.place(
                                                ObjectId.of(0, 0),
                                                10,
                                                20,
                                                0));

                assertEquals(
                                0,
                                third.addCount);

                assertEquals(
                                0,
                                third.moveCount);

                assertEquals(
                                0,
                                third.removeCount);
        }

        @Test
        void rejectsMutationAfterSpatialFailure() {
                FailingIndex index = new FailingIndex();

                index.failAdd = true;

                SpatialSystem spatial = new SpatialSystem(index);

                assertThrows(
                                IllegalStateException.class,
                                () -> spatial.place(
                                                ObjectId.of(0, 0),
                                                1,
                                                2,
                                                3));

                assertTrue(
                                spatial.isFaulted());

                assertThrows(
                                IllegalStateException.class,
                                () -> spatial.place(
                                                ObjectId.of(1, 0),
                                                4,
                                                5,
                                                6));
        }

        @Test
        void allowsReadingAfterSpatialFailure() {
                FailingIndex index = new FailingIndex();

                SpatialSystem spatial = new SpatialSystem(index);

                ObjectId id = ObjectId.of(0, 0);

                spatial.place(
                                id,
                                10,
                                20,
                                3);

                index.failMove = true;

                assertThrows(
                                IllegalStateException.class,
                                () -> spatial.move(
                                                id,
                                                30,
                                                40,
                                                5));

                assertTrue(
                                spatial.isFaulted());

                assertTrue(
                                spatial.transforms().has(id));

                assertEquals(
                                10,
                                spatial.transforms().x(id));

                assertEquals(
                                20,
                                spatial.transforms().y(id));

                assertEquals(
                                3,
                                spatial.transforms().z(id));
        }

        @Test
        void preservesRollbackFailureAsSuppressed() {
                FailingRollbackIndex first = new FailingRollbackIndex();

                FailingIndex second = new FailingIndex();

                second.failMove = true;

                SpatialSystem spatial = new SpatialSystem(
                                first,
                                second);

                ObjectId id = ObjectId.of(0, 0);

                spatial.place(
                                id,
                                10,
                                20,
                                3);

                first.failNextMove = false;
                first.failMoveNumber = 2;

                IllegalStateException failure = assertThrows(
                                IllegalStateException.class,
                                () -> spatial.move(
                                                id,
                                                30,
                                                40,
                                                5));

                assertTrue(
                                spatial.isFaulted());

                assertEquals(
                                1,
                                failure.getSuppressed().length);

                assertEquals(
                                10,
                                spatial.transforms().x(id));

                assertEquals(
                                20,
                                spatial.transforms().y(id));

                assertEquals(
                                3,
                                spatial.transforms().z(id));
        }

        private static final class FailingRollbackIndex
                        implements SpatialIndex {

                private int moveCount;
                private int failMoveNumber = -1;
                private boolean failNextMove;

                @Override
                public void add(
                                ObjectId id,
                                double x,
                                double y,
                                double z) {
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

                        if (failNextMove
                                        || moveCount == failMoveNumber) {

                                throw new IllegalStateException(
                                                "rollback failure");
                        }
                }

                @Override
                public void remove(
                                ObjectId id,
                                double x,
                                double y,
                                double z) {
                }
        }

        private static final class FailingIndex
                        implements SpatialIndex {

                private boolean failAdd;
                private boolean failMove;
                private boolean failRemove;

                @Override
                public void add(
                                ObjectId id,
                                double x,
                                double y,
                                double z) {

                        if (failAdd) {
                                throw new IllegalStateException(
                                                "add failure");
                        }
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

                        if (failMove) {
                                throw new IllegalStateException(
                                                "move failure");
                        }
                }

                @Override
                public void remove(
                                ObjectId id,
                                double x,
                                double y,
                                double z) {

                        if (failRemove) {
                                throw new IllegalStateException(
                                                "remove failure");
                        }
                }
        }
}