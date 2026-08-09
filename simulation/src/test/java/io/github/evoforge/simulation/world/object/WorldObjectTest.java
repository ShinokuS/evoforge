package io.github.evoforge.simulation.world.object;

import io.github.evoforge.simulation.world.definition.DefinitionId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldObjectTest {

    @Test
    void storesIdentity() {
        ObjectId id = ObjectId.of(3, 2);

        DefinitionId definitionId = DefinitionId.of(7);

        WorldObject object = new WorldObject(
                id,
                definitionId);

        assertEquals(
                id,
                object.id());

        assertEquals(
                definitionId,
                object.definitionId());
    }

    @Test
    void rejectsNullObjectId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldObject(
                        null,
                        DefinitionId.of(0)));
    }

    @Test
    void rejectsNullDefinitionId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldObject(
                        ObjectId.of(0, 0),
                        null));
    }
}