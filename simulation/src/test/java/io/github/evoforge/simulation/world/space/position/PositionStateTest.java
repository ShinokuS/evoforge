package io.github.evoforge.simulation.world.space.position;

import io.github.evoforge.simulation.world.object.ObjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionStateTest {

        @Test
        void addsAndReadsTransform() {
                PositionState state = new PositionState();

                ObjectId id = ObjectId.of(0, 0);

                state.add(
                                id,
                                10,
                                20,
                                3);

                assertTrue(
                                state.has(id));

                assertEquals(
                                10,
                                state.x(id));

                assertEquals(
                                20,
                                state.y(id));

                assertEquals(
                                3,
                                state.z(id));
        }

        @Test
        void reportsMissingTransform() {
                PositionState state = new PositionState();

                ObjectId id = ObjectId.of(0, 0);

                assertFalse(
                                state.has(id));

                assertThrows(
                                IllegalStateException.class,
                                () -> state.x(id));

                assertThrows(
                                IllegalStateException.class,
                                () -> state.y(id));

                assertThrows(
                                IllegalStateException.class,
                                () -> state.z(id));
        }

        @Test
        void movesTransform() {
                PositionState state = new PositionState();

                ObjectId id = ObjectId.of(0, 0);

                state.add(
                                id,
                                10,
                                20,
                                0);

                state.move(
                                id,
                                30,
                                40,
                                5);

                assertEquals(
                                30,
                                state.x(id));

                assertEquals(
                                40,
                                state.y(id));

                assertEquals(
                                5,
                                state.z(id));
        }

        @Test
        void removesTransform() {
                PositionState state = new PositionState();

                ObjectId id = ObjectId.of(0, 0);

                state.add(
                                id,
                                10,
                                20,
                                0);

                state.remove(id);

                assertFalse(
                                state.has(id));

                assertThrows(
                                IllegalStateException.class,
                                () -> state.x(id));

                assertThrows(
                                IllegalStateException.class,
                                () -> state.y(id));

                assertThrows(
                                IllegalStateException.class,
                                () -> state.z(id));
        }

        @Test
        void distinguishesObjectGenerations() {
                PositionState state = new PositionState();

                ObjectId oldId = ObjectId.of(5, 1);

                ObjectId newId = ObjectId.of(5, 2);

                state.add(
                                oldId,
                                10,
                                20,
                                0);

                state.remove(oldId);

                state.add(
                                newId,
                                30,
                                40,
                                5);

                assertFalse(
                                state.has(oldId));

                assertTrue(
                                state.has(newId));

                assertThrows(
                                IllegalStateException.class,
                                () -> state.x(oldId));

                assertEquals(
                                30,
                                state.x(newId));

                assertEquals(
                                40,
                                state.y(newId));

                assertEquals(
                                5,
                                state.z(newId));
        }

        @Test
        void staleGenerationCannotMutateCurrentTransform() {
                PositionState state = new PositionState();

                ObjectId oldId = ObjectId.of(5, 1);

                ObjectId newId = ObjectId.of(5, 2);

                state.add(
                                oldId,
                                10,
                                20,
                                0);

                state.remove(oldId);

                state.add(
                                newId,
                                30,
                                40,
                                5);

                assertThrows(
                                IllegalStateException.class,
                                () -> state.move(
                                                oldId,
                                                100,
                                                200,
                                                300));

                assertThrows(
                                IllegalStateException.class,
                                () -> state.remove(oldId));

                assertEquals(
                                30,
                                state.x(newId));

                assertEquals(
                                40,
                                state.y(newId));

                assertEquals(
                                5,
                                state.z(newId));
        }

        @Test
        void growsForLargeSlot() {
                PositionState state = new PositionState();

                ObjectId id = ObjectId.of(100, 0);

                state.add(
                                id,
                                1,
                                2,
                                3);

                assertTrue(
                                state.has(id));

                assertEquals(
                                1,
                                state.x(id));

                assertEquals(
                                2,
                                state.y(id));

                assertEquals(
                                3,
                                state.z(id));
        }

        @Test
        void supportsFullIntegerCoordinateRange() {
                PositionState state = new PositionState();

                ObjectId id = ObjectId.of(0, 0);

                state.add(
                                id,
                                Integer.MIN_VALUE,
                                0,
                                Integer.MAX_VALUE);

                assertEquals(
                                Integer.MIN_VALUE,
                                state.x(id));

                assertEquals(
                                0,
                                state.y(id));

                assertEquals(
                                Integer.MAX_VALUE,
                                state.z(id));
        }

        @Test
        void rejectsDuplicateTransform() {
                PositionState state = new PositionState();

                ObjectId id = ObjectId.of(0, 0);

                state.add(
                                id,
                                1,
                                2,
                                3);

                assertThrows(
                                IllegalStateException.class,
                                () -> state.add(
                                                id,
                                                4,
                                                5,
                                                6));

                assertEquals(
                                1,
                                state.x(id));

                assertEquals(
                                2,
                                state.y(id));

                assertEquals(
                                3,
                                state.z(id));
        }

        @Test
        void rejectsMoveForMissingTransform() {
                PositionState state = new PositionState();

                ObjectId id = ObjectId.of(0, 0);

                assertThrows(
                                IllegalStateException.class,
                                () -> state.move(
                                                id,
                                                1,
                                                2,
                                                3));
        }

        @Test
        void rejectsRemoveForMissingTransform() {
                PositionState state = new PositionState();

                ObjectId id = ObjectId.of(0, 0);

                assertThrows(
                                IllegalStateException.class,
                                () -> state.remove(id));
        }

        @Test
        void hasReturnsFalseForNull() {
                PositionState state = new PositionState();

                assertFalse(
                                state.has(null));
        }
}