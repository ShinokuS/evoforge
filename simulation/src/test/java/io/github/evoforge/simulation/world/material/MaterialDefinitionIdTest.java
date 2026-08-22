package io.github.evoforge.simulation.world.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class MaterialDefinitionIdTest {

    @Test
    void createsId() {
        assertEquals(0, MaterialDefinitionId.of(0).asInt());
        assertEquals(100, MaterialDefinitionId.of(100).asInt());
    }

    @Test
    void comparesByValue() {
        assertEquals(
                MaterialDefinitionId.of(42),
                MaterialDefinitionId.of(42));
        assertNotEquals(
                MaterialDefinitionId.of(42),
                MaterialDefinitionId.of(43));
    }

    @Test
    void rejectsNegativeValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MaterialDefinitionId.of(-1));
    }
}
