package io.github.evoforge.simulation.world.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefinitionIdTest {

    @Test
    void createsDefinitionId() {
        DefinitionId id = DefinitionId.of(42);

        assertEquals(42, id.asInt());
    }

    @Test
    void equalIdsAreEqual() {
        DefinitionId first = DefinitionId.of(42);
        DefinitionId second = DefinitionId.of(42);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void differentIdsAreNotEqual() {
        DefinitionId first = DefinitionId.of(42);
        DefinitionId second = DefinitionId.of(43);

        assertNotEquals(first, second);
    }

    @Test
    void rejectsNegativeValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DefinitionId.of(-1));
    }

    @Test
    void supportsMaximumValue() {
        DefinitionId id = DefinitionId.of(Integer.MAX_VALUE);

        assertEquals(Integer.MAX_VALUE, id.asInt());
    }

    @Test
    void hasReadableStringRepresentation() {
        DefinitionId id = DefinitionId.of(42);

        assertEquals("DefinitionId[42]", id.toString());
    }
}