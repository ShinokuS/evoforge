package io.github.evoforge.simulation.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskHandleTest {

    @Test
    void createsHandle() {
        TaskHandle handle = TaskHandle.of(42);

        assertEquals(
                42,
                handle.asLong());
    }

    @Test
    void equalHandlesAreEqual() {
        assertEquals(
                TaskHandle.of(42),
                TaskHandle.of(42));
    }

    @Test
    void differentHandlesAreNotEqual() {
        assertNotEquals(
                TaskHandle.of(1),
                TaskHandle.of(2));
    }

    @Test
    void rejectsNegativeValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TaskHandle.of(-1));
    }
}