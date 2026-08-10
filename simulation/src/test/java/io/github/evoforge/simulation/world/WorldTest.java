package io.github.evoforge.simulation.world;

import io.github.evoforge.simulation.definition.DefinitionId;
import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.WorldObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldTest {

    @Test
    void ownsObjectInfrastructure() {
        DefinitionRegistry definitions = new DefinitionRegistry();

        definitions.register("core:test");

        World world = new World(definitions);

        TestWorldObject object = world.objectFactory().create(
                "core:test",
                TestWorldObject::new);

        assertSame(
                object,
                world.objects().get(object.id()));
    }

    @Test
    void rejectsNullDefinitions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new World(null));
    }

    private static final class TestWorldObject
            extends WorldObject {

        private TestWorldObject(
                ObjectId id,
                DefinitionId definitionId) {
            super(id, definitionId);
        }
    }
}