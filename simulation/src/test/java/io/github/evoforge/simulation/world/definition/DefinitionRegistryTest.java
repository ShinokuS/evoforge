package io.github.evoforge.simulation.world.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionRegistryTest {

    @Test
    void registersDefinition() {
        DefinitionRegistry registry = new DefinitionRegistry();
        ObjectDefinition definition = new TestDefinition("food.apple");

        DefinitionId id = registry.register(definition);

        assertEquals(DefinitionId.of(0), id);
        assertEquals(1, registry.size());
    }

    @Test
    void assignsSequentialIds() {
        DefinitionRegistry registry = new DefinitionRegistry();

        DefinitionId first = registry.register(new TestDefinition("food.apple"));

        DefinitionId second = registry.register(new TestDefinition("food.bread"));

        assertEquals(DefinitionId.of(0), first);
        assertEquals(DefinitionId.of(1), second);
    }

    @Test
    void returnsDefinitionById() {
        DefinitionRegistry registry = new DefinitionRegistry();
        ObjectDefinition definition = new TestDefinition("food.apple");

        DefinitionId id = registry.register(definition);

        assertSame(definition, registry.get(id));
    }

    @Test
    void returnsDefinitionByKey() {
        DefinitionRegistry registry = new DefinitionRegistry();
        ObjectDefinition definition = new TestDefinition("food.apple");

        registry.register(definition);

        assertSame(definition, registry.get("food.apple"));
    }

    @Test
    void returnsIdByKey() {
        DefinitionRegistry registry = new DefinitionRegistry();

        DefinitionId id = registry.register(new TestDefinition("food.apple"));

        assertEquals(id, registry.idOf("food.apple"));
    }

    @Test
    void rejectsDuplicateKey() {
        DefinitionRegistry registry = new DefinitionRegistry();

        registry.register(new TestDefinition("food.apple"));

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(
                        new TestDefinition("food.apple")));

        assertEquals(1, registry.size());
    }

    @Test
    void rejectsNullDefinition() {
        DefinitionRegistry registry = new DefinitionRegistry();

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(null));
    }

    @Test
    void returnsNullForUnknownId() {
        DefinitionRegistry registry = new DefinitionRegistry();

        assertNull(registry.get(DefinitionId.of(100)));
    }

    @Test
    void returnsNullForUnknownKey() {
        DefinitionRegistry registry = new DefinitionRegistry();

        assertNull(registry.get("food.unknown"));
        assertNull(registry.idOf("food.unknown"));
    }

    @Test
    void handlesNullLookup() {
        DefinitionRegistry registry = new DefinitionRegistry();

        assertNull(registry.get((DefinitionId) null));
        assertNull(registry.get((String) null));
        assertNull(registry.idOf(null));
    }

    @Test
    void freezesRegistry() {
        DefinitionRegistry registry = new DefinitionRegistry();

        assertFalse(registry.isFrozen());

        registry.freeze();

        assertTrue(registry.isFrozen());
    }

    @Test
    void rejectsRegistrationAfterFreeze() {
        DefinitionRegistry registry = new DefinitionRegistry();

        registry.register(new TestDefinition("food.apple"));
        registry.freeze();

        assertThrows(
                IllegalStateException.class,
                () -> registry.register(
                        new TestDefinition("food.bread")));

        assertEquals(1, registry.size());
    }

    @Test
    void allowsReadingAfterFreeze() {
        DefinitionRegistry registry = new DefinitionRegistry();
        ObjectDefinition definition = new TestDefinition("food.apple");

        DefinitionId id = registry.register(definition);
        registry.freeze();

        assertSame(definition, registry.get(id));
        assertSame(definition, registry.get("food.apple"));
        assertEquals(id, registry.idOf("food.apple"));
    }

    private static final class TestDefinition
            extends ObjectDefinition {

        private TestDefinition(String key) {
            super(key);
        }
    }
}