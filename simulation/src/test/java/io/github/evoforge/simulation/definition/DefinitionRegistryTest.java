package io.github.evoforge.simulation.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionRegistryTest {

    @Test
    void rejectsNullIdProducedByFactory() {
        DefinitionRegistry<DefinitionId> registry =
                new DefinitionRegistry<>(
                        index -> null,
                        DefinitionId::asInt);

        assertThrows(
                IllegalStateException.class,
                () -> registry.register("core:test"));
    }

    @Test
    void rejectsInconsistentIdFactoryAndIndexer() {
        DefinitionRegistry<DefinitionId> registry =
                new DefinitionRegistry<>(
                        index -> DefinitionId.of(index + 1),
                        DefinitionId::asInt);

        assertThrows(
                IllegalStateException.class,
                () -> registry.register("core:test"));
    }

    @Test
    void failedIdCreationDoesNotRegisterDefinition() {
        DefinitionRegistry<DefinitionId> registry =
                new DefinitionRegistry<>(
                        index -> DefinitionId.of(index + 1),
                        DefinitionId::asInt);

        assertThrows(
                IllegalStateException.class,
                () -> registry.register("core:test"));

        assertNull(registry.resolve("core:test"));
        assertEquals(0, registry.size());
    }

    @Test
    void registersKey() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        DefinitionId id = registry.register("core:apple");

        assertEquals(DefinitionId.of(0), id);
        assertEquals(1, registry.size());
    }

    @Test
    void assignsSequentialIds() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        DefinitionId first = registry.register("core:apple");

        DefinitionId second = registry.register("core:wolf");

        assertEquals(DefinitionId.of(0), first);
        assertEquals(DefinitionId.of(1), second);
    }

    @Test
    void returnsIdByKey() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        DefinitionId id = registry.register("core:apple");

        assertEquals(
                id,
                registry.idOf("core:apple"));
    }

    @Test
    void returnsKeyById() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        DefinitionId id = registry.register("core:apple");

        assertEquals(
                "core:apple",
                registry.keyOf(id));
    }

    @Test
    void rejectsDuplicateKey() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        registry.register("core:apple");

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register("core:apple"));

        assertEquals(1, registry.size());
    }

    @Test
    void rejectsNullKey() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(null));
    }

    @Test
    void rejectsBlankKey() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register("   "));
    }

    @Test
    void rejectsInvalidKeyFormat() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register("apple"));

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register("object.apple"));

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register("Core:Apple"));

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register("core:"));

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(":apple"));

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register("core:apple:test"));
    }

    @Test
    void acceptsValidKeyFormats() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        registry.register("core:apple");
        registry.register("core:rotten_apple");
        registry.register("core:oak-tree");
        registry.register("mod.example:magic.apple");

        assertEquals(4, registry.size());
    }

    @Test
    void returnsNullForUnknownKey() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        assertNull(
                registry.idOf("core:unknown"));
    }

    @Test
    void returnsNullForUnknownId() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        assertNull(
                registry.keyOf(DefinitionId.of(100)));
    }

    @Test
    void handlesNullLookup() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        assertNull(registry.idOf(null));
        assertNull(registry.resolve(null));
        assertNull(registry.keyOf(null));
    }

    @Test
    void freezesRegistry() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        assertFalse(registry.isFrozen());

        registry.freeze();

        assertTrue(registry.isFrozen());
    }

    @Test
    void rejectsRegistrationAfterFreeze() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        registry.register("core:apple");
        registry.freeze();

        assertThrows(
                IllegalStateException.class,
                () -> registry.register("core:wolf"));

        assertEquals(1, registry.size());
    }

    @Test
    void allowsReadingAfterFreeze() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        DefinitionId id = registry.register("core:apple");

        registry.freeze();

        assertEquals(
                id,
                registry.idOf("core:apple"));

        assertEquals(
                "core:apple",
                registry.keyOf(id));
    }

    @Test
    void resolvesIdByKey() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        DefinitionId id = registry.register("core:apple");

        assertEquals(
                id,
                registry.resolve("core:apple"));
    }

    @Test
    void returnsNullWhenResolvingUnknownKey() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        assertNull(
                registry.resolve("core:unknown"));
    }

    @Test
    void containsRegisteredDefinitionId() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        DefinitionId id = registry.register("core:apple");

        assertTrue(
                registry.contains(id));
    }

    @Test
    void doesNotContainUnknownDefinitionId() {
        DefinitionRegistry<DefinitionId> registry = new DefinitionRegistry<>(DefinitionId::of, DefinitionId::asInt);

        registry.register("core:apple");

        assertFalse(
                registry.contains(
                        DefinitionId.of(100)));
    }
}
