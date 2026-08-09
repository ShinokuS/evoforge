package io.github.evoforge.simulation.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HandlerIdTest {

    @Test
    void createsHandlerId() {
        HandlerId id = HandlerId.of(42);

        assertEquals(
                42,
                id.asInt());
    }

    @Test
    void equalIdsAreEqual() {
        assertEquals(
                HandlerId.of(42),
                HandlerId.of(42));
    }

    @Test
    void differentIdsAreNotEqual() {
        assertNotEquals(
                HandlerId.of(1),
                HandlerId.of(2));
    }

    @Test
    void rejectsNegativeValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> HandlerId.of(-1));
    }
}