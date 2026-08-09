package io.github.evoforge.simulation.world.mechanics.physical;

import io.github.evoforge.simulation.world.definition.DefinitionId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicalDefinitionsTest {

    @Test
    void storesMass() {
        PhysicalDefinitions definitions = new PhysicalDefinitions();

        DefinitionId id = DefinitionId.of(3);

        definitions.put(id, 0.18);

        assertTrue(definitions.has(id));
        assertEquals(0.18, definitions.mass(id));
    }

    @Test
    void returnsFalseForMissingDefinition() {
        PhysicalDefinitions definitions = new PhysicalDefinitions();

        assertFalse(definitions.has(DefinitionId.of(3)));
    }

    @Test
    void growsForLargeDefinitionId() {
        PhysicalDefinitions definitions = new PhysicalDefinitions();

        DefinitionId id = DefinitionId.of(100);

        definitions.put(id, 2.5);

        assertTrue(definitions.has(id));
        assertEquals(2.5, definitions.mass(id));
    }

    @Test
    void rejectsDuplicateDefinition() {
        PhysicalDefinitions definitions = new PhysicalDefinitions();

        DefinitionId id = DefinitionId.of(1);

        definitions.put(id, 1.0);

        assertThrows(
                IllegalStateException.class,
                () -> definitions.put(id, 2.0));
    }

    @Test
    void rejectsInvalidMass() {
        PhysicalDefinitions definitions = new PhysicalDefinitions();

        DefinitionId id = DefinitionId.of(0);

        assertThrows(
                IllegalArgumentException.class,
                () -> definitions.put(id, 0));

        assertThrows(
                IllegalArgumentException.class,
                () -> definitions.put(id, -1));

        assertThrows(
                IllegalArgumentException.class,
                () -> definitions.put(id, Double.NaN));

        assertThrows(
                IllegalArgumentException.class,
                () -> definitions.put(
                        id,
                        Double.POSITIVE_INFINITY));
    }

    @Test
    void rejectsMissingMassLookup() {
        PhysicalDefinitions definitions = new PhysicalDefinitions();

        assertThrows(
                IllegalArgumentException.class,
                () -> definitions.mass(DefinitionId.of(0)));
    }

    @Test
    void handlesNullId() {
        PhysicalDefinitions definitions = new PhysicalDefinitions();

        assertFalse(definitions.has(null));

        assertThrows(
                IllegalArgumentException.class,
                () -> definitions.put(null, 1.0));
    }

    @Test
    void freezesDefinitions() {
        PhysicalDefinitions definitions = new PhysicalDefinitions();

        definitions.freeze();

        assertTrue(definitions.isFrozen());
    }

    @Test
    void rejectsPutAfterFreeze() {
        PhysicalDefinitions definitions = new PhysicalDefinitions();

        definitions.freeze();

        assertThrows(
                IllegalStateException.class,
                () -> definitions.put(
                        DefinitionId.of(0),
                        0.18));
    }

    @Test
    void remainsReadableAfterFreeze() {
        PhysicalDefinitions definitions = new PhysicalDefinitions();

        DefinitionId id = DefinitionId.of(0);

        definitions.put(id, 0.18);

        definitions.freeze();

        assertTrue(definitions.has(id));

        assertEquals(
                0.18,
                definitions.mass(id));
    }
}