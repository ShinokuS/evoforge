package io.github.evoforge.simulation.world.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjectDefinitionTest {

    @Test
    void storesKey() {
        ObjectDefinition definition = new TestDefinition("food.apple");

        assertEquals("food.apple", definition.key());
    }

    @Test
    void rejectsNullKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestDefinition(null));
    }

    @Test
    void rejectsBlankKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestDefinition("   "));
    }

    private static final class TestDefinition
            extends ObjectDefinition {

        private TestDefinition(String key) {
            super(key);
        }
    }
}