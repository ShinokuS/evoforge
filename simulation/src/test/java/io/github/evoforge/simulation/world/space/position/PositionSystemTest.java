package io.github.evoforge.simulation.world.space.position;

import io.github.evoforge.simulation.world.object.ObjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionSystemTest {

        @Test
        void placesObject() {
                RecordingIndex index = new RecordingIndex();

                PositionSystem spatial = new PositionSystem(index);

                ObjectId id = ObjectId.of(0, 0);

                spatial.place(
                                id,
                                10,
                                20,
                                3);

                PositionLookup transforms = spatial.positions();

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

                PositionSystem spatial = new PositionSystem(index);

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

                PositionLookup transforms = spatial.positions();

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

                PositionSystem spatial = new PositionSystem(index);

                ObjectId id = ObjectId.of(0, 0);

                spatial.place(
                                id,
                                10,
                                20,
                                3);

                spatial.remove(id);

                assertFalse(
                                spatial.positions().has(id));

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

                PositionSystem spatial = new PositionSystem(
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
                PositionSystem spatial = new PositionSystem();

                ObjectId id = ObjectId.of(0, 0);

                spatial.place(
                                id,
                                1,
                                2,
                                3);

                assertTrue(
                                spatial.positions().has(id));

                spatial.move(
                                id,
                                4,
                                5,
                                6);

                assertEquals(
                                4,
                                spatial.positions().x(id));

                assertEquals(
                                5,
                                spatial.positions().y(id));

                assertEquals(
                                6,
                                spatial.positions().z(id));

                spatial.remove(id);

                assertFalse(
                                spatial.positions().has(id));
        }

        @Test
        void rejectsNullIndexesArray() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new PositionSystem(
                                                (ObjectPositionIndex[]) null));
        }

        @Test
        void rejectsNullIndex() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new PositionSystem(
                                                new RecordingIndex(),
                                                null));
        }

        @Test
        void rejectsMoveForObjectWithoutTransform() {
                PositionSystem spatial = new PositionSystem();

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
                PositionSystem spatial = new PositionSystem();

                assertThrows(
                                IllegalStateException.class,
                                () -> spatial.remove(
                                                ObjectId.of(0, 0)));
        }

        @Test
        void failedPlaceRollsBackPreviousChanges() {
                RecordingIndex first = new RecordingIndex();

                FailingIndex second = new FailingIndex();

                second.failAdd = true;

                PositionSystem spatial = new PositionSystem(
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
                                spatial.positions().has(id));

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

                PositionSystem spatial = new PositionSystem(
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
                                spatial.positions().x(id));

                assertEquals(
                                20,
                                spatial.positions().y(id));

                assertEquals(
                                3,
                                spatial.positions().z(id));

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

                PositionSystem spatial = new PositionSystem(
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
                                spatial.positions().has(id));

                assertEquals(
                                10,
                                spatial.positions().x(id));

                assertEquals(
                                20,
                                spatial.positions().y(id));

                assertEquals(
                                3,
                                spatial.positions().z(id));

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

                PositionSystem spatial = new PositionSystem(
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

                PositionSystem spatial = new PositionSystem(index);

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

                PositionSystem spatial = new PositionSystem(index);

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
                                spatial.positions().has(id));

                assertEquals(
                                10,
                                spatial.positions().x(id));

                assertEquals(
                                20,
                                spatial.positions().y(id));

                assertEquals(
                                3,
                                spatial.positions().z(id));
        }

        @Test
        void preservesRollbackFailureAsSuppressed() {
                FailingRollbackIndex first = new FailingRollbackIndex();

                FailingIndex second = new FailingIndex();

                second.failMove = true;

                PositionSystem spatial = new PositionSystem(
                                first,
                                second);

                ObjectId id = ObjectId.of(0, 0);

                spatial.place(
                                id,
                                10,
                                20,
                                3);

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
                                spatial.positions().x(id));

                assertEquals(
                                20,
                                spatial.positions().y(id));

                assertEquals(
                                3,
                                spatial.positions().z(id));
        }

        private static final class RecordingIndex
                        implements ObjectPositionIndex {

                private int addCount;
                private int moveCount;
                private int removeCount;

                private ObjectId lastId;

                private int x;
                private int y;
                private int z;

                private int oldX;
                private int oldY;
                private int oldZ;

                private int newX;
                private int newY;
                private int newZ;

                @Override
                public void add(
                                ObjectId id,
                                int x,
                                int y,
                                int z) {

                        addCount++;

                        lastId = id;

                        this.x = x;
                        this.y = y;
                        this.z = z;
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
                                int x,
                                int y,
                                int z) {

                        removeCount++;

                        lastId = id;

                        this.x = x;
                        this.y = y;
                        this.z = z;
                }
        }

        private static final class FailingRollbackIndex
                        implements ObjectPositionIndex {

                private int moveCount;
                private int failMoveNumber = -1;

                @Override
                public void add(
                                ObjectId id,
                                int x,
                                int y,
                                int z) {
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

                        moveCount++;

                        if (moveCount == failMoveNumber) {
                                throw new IllegalStateException(
                                                "rollback failure");
                        }
                }

                @Override
                public void remove(
                                ObjectId id,
                                int x,
                                int y,
                                int z) {
                }
        }

        private static final class FailingIndex
                        implements ObjectPositionIndex {

                private boolean failAdd;
                private boolean failMove;
                private boolean failRemove;

                @Override
                public void add(
                                ObjectId id,
                                int x,
                                int y,
                                int z) {

                        if (failAdd) {
                                throw new IllegalStateException(
                                                "add failure");
                        }
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

                        if (failMove) {
                                throw new IllegalStateException(
                                                "move failure");
                        }
                }

                @Override
                public void remove(
                                ObjectId id,
                                int x,
                                int y,
                                int z) {

                        if (failRemove) {
                                throw new IllegalStateException(
                                                "remove failure");
                        }
                }
        }
}