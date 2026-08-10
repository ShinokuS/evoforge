package io.github.evoforge.simulation.world.object.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class ObjectDefinitionIdTest {

    @Test
    void createsId() {
        assertEquals(0, ObjectDefinitionId.of(0).asInt());
        assertEquals(100, ObjectDefinitionId.of(100).asInt());
    }

    @Test
    void comparesByValue() {
        assertEquals(
                ObjectDefinitionId.of(42),
                ObjectDefinitionId.of(42));
        assertNotEquals(
                ObjectDefinitionId.of(42),
                ObjectDefinitionId.of(43));
    }

    @Test
    void rejectsNegativeValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ObjectDefinitionId.of(-1));
    }
}
