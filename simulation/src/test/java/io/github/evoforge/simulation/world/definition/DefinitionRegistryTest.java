package io.github.evoforge.simulation.world.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionRegistryTest {

    @Test
    void registersKey() {
        DefinitionRegistry registry = new DefinitionRegistry();

        DefinitionId id = registry.register("object.apple");

        assertEquals(DefinitionId.of(0), id);
        assertEquals(1, registry.size());
    }

    @Test
    void assignsSequentialIds() {
        DefinitionRegistry registry = new DefinitionRegistry();

        DefinitionId first = registry.register("object.apple");
        DefinitionId second = registry.register("animal.wolf");

        assertEquals(DefinitionId.of(0), first);
        assertEquals(DefinitionId.of(1), second);
    }

    @Test
    void returnsIdByKey() {
        DefinitionRegistry registry = new DefinitionRegistry();

        DefinitionId id = registry.register("object.apple");

        assertEquals(id, registry.idOf("object.apple"));
    }

    @Test
    void returnsKeyById() {
        DefinitionRegistry registry = new DefinitionRegistry();

        DefinitionId id = registry.register("object.apple");

        assertEquals("object.apple", registry.keyOf(id));
    }

    @Test
    void rejectsDuplicateKey() {
        DefinitionRegistry registry = new DefinitionRegistry();

        registry.register("object.apple");

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register("object.apple"));

        assertEquals(1, registry.size());
    }

    @Test
    void rejectsNullKey() {
        DefinitionRegistry registry = new DefinitionRegistry();

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(null));
    }

    @Test
    void rejectsBlankKey() {
        DefinitionRegistry registry = new DefinitionRegistry();

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register("   "));
    }

    @Test
    void returnsNullForUnknownKey() {
        DefinitionRegistry registry = new DefinitionRegistry();

        assertNull(registry.idOf("object.unknown"));
    }

    @Test
    void returnsNullForUnknownId() {
        DefinitionRegistry registry = new DefinitionRegistry();

        assertNull(registry.keyOf(DefinitionId.of(100)));
    }

    @Test
    void handlesNullLookup() {
        DefinitionRegistry registry = new DefinitionRegistry();

        assertNull(registry.idOf(null));
        assertNull(registry.keyOf(null));
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

        registry.register("object.apple");
        registry.freeze();

        assertThrows(
                IllegalStateException.class,
                () -> registry.register("animal.wolf"));
    }

    @Test
    void allowsReadingAfterFreeze() {
        DefinitionRegistry registry = new DefinitionRegistry();

        DefinitionId id = registry.register("object.apple");

        registry.freeze();

        assertEquals(id, registry.idOf("object.apple"));
        assertEquals("object.apple", registry.keyOf(id));
    }
}