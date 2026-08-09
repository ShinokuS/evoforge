package io.github.evoforge.simulation.world.spatial;

import io.github.evoforge.simulation.world.object.ObjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformStateTest {

        @Test
        void addsAndReadsTransform() {
                TransformState state = new TransformState();

                ObjectId id = ObjectId.of(0, 0);

                state.add(
                                id,
                                10.5,
                                20.25,
                                3.0);

                assertTrue(
                                state.has(id));

                assertEquals(
                                10.5,
                                state.x(id));

                assertEquals(
                                20.25,
                                state.y(id));

                assertEquals(
                                3.0,
                                state.z(id));
        }

        @Test
        void reportsMissingTransform() {
                TransformState state = new TransformState();

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
                TransformState state = new TransformState();

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
                TransformState state = new TransformState();

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
        }

        @Test
        void distinguishesObjectGenerations() {
                TransformState state = new TransformState();

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
        }

        @Test
        void growsForLargeSlot() {
                TransformState state = new TransformState();

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
        void rejectsDuplicateTransform() {
                TransformState state = new TransformState();

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
        }

        @Test
        void rejectsNonFiniteCoordinatesOnAdd() {
                TransformState state = new TransformState();

                assertThrows(
                                IllegalArgumentException.class,
                                () -> state.add(
                                                ObjectId.of(0, 0),
                                                Double.NaN,
                                                0,
                                                0));

                assertThrows(
                                IllegalArgumentException.class,
                                () -> state.add(
                                                ObjectId.of(0, 0),
                                                0,
                                                Double.POSITIVE_INFINITY,
                                                0));

                assertThrows(
                                IllegalArgumentException.class,
                                () -> state.add(
                                                ObjectId.of(0, 0),
                                                0,
                                                0,
                                                Double.NEGATIVE_INFINITY));
        }

        @Test
        void rejectsNonFiniteCoordinatesOnMove() {
                TransformState state = new TransformState();

                ObjectId id = ObjectId.of(0, 0);

                state.add(
                                id,
                                1,
                                2,
                                3);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> state.move(
                                                id,
                                                Double.NaN,
                                                2,
                                                3));

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
                TransformState state = new TransformState();

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
                TransformState state = new TransformState();

                ObjectId id = ObjectId.of(0, 0);

                assertThrows(
                                IllegalStateException.class,
                                () -> state.remove(id));
        }

        @Test
        void hasReturnsFalseForNull() {
                TransformState state = new TransformState();

                assertFalse(
                                state.has(null));
        }
}