package io.github.evoforge.simulation.world;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.world.object.physical.PhysicalDefinitionCompiler;
import io.github.evoforge.simulation.world.object.physical.PhysicalDefinitions;
import io.github.evoforge.simulation.world.object.ObjectFactory;
import io.github.evoforge.simulation.world.object.ObjectRepository;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionBootstrap;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldDefinitionIntegrationTest {

    @TempDir
    Path directory;

    @Test
    void createsWorldObjectFromLoadedDefinition()
            throws IOException {

        Files.writeString(
                directory.resolve("apple.json"),
                """
                        {
                            "key": "core:apple",
                            "aspects": {
                                "physical": {
                                    "mass": 0.18
                                }
                            }
                        }
                        """,
                UTF_8);

        PhysicalDefinitions physical = new PhysicalDefinitions();
        ObjectDefinitionBootstrap bootstrap = new ObjectDefinitionBootstrap(
                new PhysicalDefinitionCompiler(physical));
        DefinitionRegistry<ObjectDefinitionId> definitions = bootstrap.load(directory);

        ObjectRepository objects = new ObjectRepository();
        ObjectFactory objectFactory = new ObjectFactory(objects, definitions);

        WorldObject apple = objectFactory.create("core:apple");

        assertEquals(definitions.resolve("core:apple"), apple.definitionId());
        assertEquals(0.18, physical.mass(apple.definitionId()));
        assertTrue(objects.isAlive(apple.id()));
        assertSame(apple, objects.get(apple.id()));
    }
}
