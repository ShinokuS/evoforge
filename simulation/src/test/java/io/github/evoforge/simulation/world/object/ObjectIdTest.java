package io.github.evoforge.simulation.world.object;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjectIdTest {

    @Test
    void createsIdFromSlotAndGeneration() {
        ObjectId id = ObjectId.of(42, 7);

        assertEquals(42, id.slot());
        assertEquals(7, id.generation());
    }

    @Test
    void packsSlotAndGenerationIntoLong() {
        ObjectId id = ObjectId.of(42, 7);

        long expected = ((long) 7 << 32) | 42L;

        assertEquals(expected, id.asLong());
    }

    @Test
    void equalIdsAreEqual() {
        ObjectId first = ObjectId.of(42, 7);
        ObjectId second = ObjectId.of(42, 7);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void differentSlotsProduceDifferentIds() {
        ObjectId first = ObjectId.of(42, 7);
        ObjectId second = ObjectId.of(43, 7);

        assertNotEquals(first, second);
    }

    @Test
    void differentGenerationsProduceDifferentIds() {
        ObjectId first = ObjectId.of(42, 7);
        ObjectId second = ObjectId.of(42, 8);

        assertNotEquals(first, second);
    }

    @Test
    void rejectsNegativeSlot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ObjectId.of(-1, 0));
    }

    @Test
    void rejectsNegativeGeneration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ObjectId.of(0, -1));
    }

    @Test
    void supportsMaximumValues() {
        ObjectId id = ObjectId.of(
                Integer.MAX_VALUE,
                Integer.MAX_VALUE);

        assertEquals(Integer.MAX_VALUE, id.slot());
        assertEquals(Integer.MAX_VALUE, id.generation());
    }

    @Test
    void hasReadableStringRepresentation() {
        ObjectId id = ObjectId.of(42, 7);

        assertEquals("ObjectId[42:7]", id.toString());
    }
}