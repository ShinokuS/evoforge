package io.github.evoforge.simulation.world.landscape.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class LandscapeDefinitionIdTest {

    @Test
    void createsId() {
        assertEquals(0, LandscapeDefinitionId.of(0).asInt());
        assertEquals(100, LandscapeDefinitionId.of(100).asInt());
    }

    @Test
    void comparesByValue() {
        assertEquals(
                LandscapeDefinitionId.of(42),
                LandscapeDefinitionId.of(42));
        assertNotEquals(
                LandscapeDefinitionId.of(42),
                LandscapeDefinitionId.of(43));
    }

    @Test
    void rejectsNegativeValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LandscapeDefinitionId.of(-1));
    }
}
